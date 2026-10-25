package com.rocketcredit.backend.payments;

import com.rocketcredit.backend.AbstractIntegrationTest;
import com.rocketcredit.backend.analysis.*;
import com.rocketcredit.backend.applications.*;
import com.rocketcredit.backend.common.ApiException;
import com.rocketcredit.backend.users.UserRepository;
import java.math.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"rocket.payments.poll-delay=3600000","rocket.payments.collection-enabled=true","rocket.affordability.poll-delay=3600000"})
class PaymentIntegrationTest extends AbstractIntegrationTest {
    @Autowired PaymentService payments; @Autowired PaymentLedger ledger; @Autowired CreditQuotes quotes;
    @Autowired PaymentQueries queries; @Autowired ApplicationService applications; @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users; @Autowired RepaymentWorker worker; @Autowired LoanObligations obligations;
    @Autowired com.rocketcredit.backend.auth.RegistrationService registration;
    @Autowired RepaymentCalculator pricing;
    @Autowired com.rocketcredit.backend.affordability.AffordabilityJobs jobs;
    @MockBean AnalysisClient analysis; @MockBean Clock clock;
    private Instant instant; private long user; private MockHttpSession session;
    @BeforeEach void setup() throws Exception {
        instant=Instant.parse("2026-10-09T10:00:00Z");
        when(clock.instant()).thenAnswer(i->instant);when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(clock.withZone(any())).thenAnswer(i->Clock.fixed(instant,i.getArgument(0)));
        when(analysis.score(any())).thenReturn(new AnalysisDecision("APPROVED",new BigDecimal("0.95"),new BigDecimal("1500"),"USD",List.of(),Map.of(),"rules-v4",false,"NOT_REQUESTED",null,Map.of()));
        String email=uniqueEmail();session=registerAndLogin(email);user=users.findByEmail(email).orElseThrow().getId();ledger.ensure(user);
    }
    private String key(){return UUID.randomUUID().toString();}
    private long application(String unit,int n) {
        var quote=quotes.create(user,new CreditQuotes.Request("markethub",null,new BigDecimal("300"),unit,n));
        return applications.submit(user,new ApplicationDtos.SubmitRequest("markethub",new BigDecimal("300"),null,false,quote.id()),key()).id();
    }
    private long loan(String unit,int n){return ((Number)payments.accept(user,application(unit,n),key()).get("loanId")).longValue();}
    private long installment(long loan){return jdbc.queryForObject("SELECT id FROM repayment_installments WHERE loan_id=? AND paid_at IS NULL ORDER BY sequence LIMIT 1",Long.class,loan);}
    private void funded(String amount){payments.topUp(user,key(),new BigDecimal(amount));}
    private void reconcile(){assertThat(ledger.balance(user)).isEqualByComparingTo(jdbc.queryForObject("SELECT COALESCE(sum(amount),0) FROM wallet_entries WHERE user_id=?",BigDecimal.class,user));}
    private List<String> concurrently(Callable<String> first,Callable<String> second) throws Exception {
        try(var pool=Executors.newFixedThreadPool(2)){var a=pool.submit(first);var b=pool.submit(second);return List.of(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS));}
    }
    private String outcome(Runnable r){try{r.run();return "OK";}catch(ApiException e){return e.getCode();}}
    @Test void topupReplayPurchaseHistoryAndCharts() {
        String key=key();payments.topUp(user,key,new BigDecimal("100"));payments.topUp(user,key,new BigDecimal("100.00"));
        assertThat(ledger.balance(user)).isEqualByComparingTo("100");
        assertThatThrownBy(()->payments.topUp(user,key,new BigDecimal("101"))).isInstanceOf(ApiException.class).hasMessageContaining("different request");
        long product=jdbc.queryForObject("SELECT p.id FROM products p JOIN partners s ON s.id=p.partner_id WHERE s.slug='markethub' AND p.price<100 ORDER BY p.price LIMIT 1",Long.class);
        var request=new PaymentService.Purchase("markethub",product);String purchaseKey=key();
        payments.purchase(user,purchaseKey,request);payments.purchase(user,purchaseKey,request);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transactions WHERE user_id=? AND payment_method='BALANCE'",Long.class,user)).isEqualTo(1);
        assertThat(((List<?>)queries.history(user,null,"PURCHASE",null,null,0,100).get("items")).stream().filter(o->((Map<?,?>)o).get("paymentMethod").equals("BALANCE")).count()).isEqualTo(1);
        var chart=queries.chart(user,"30d");assertThat(chart.get("closingBalance")).isEqualTo(ledger.balance(user).toPlainString());reconcile();
    }
    @Test void acceptanceIsExplicitUniqueAndDoesNotCreditWallet() {
        long app=application("WEEK",3);assertThat(jdbc.queryForObject("SELECT count(*) FROM loans WHERE user_id=?",Long.class,user)).isZero();
        long loan=((Number)payments.accept(user,app,key()).get("loanId")).longValue();
        assertThat(payments.accept(user,app,key()).get("loanId")).isEqualTo(loan);
        assertThat(ledger.balance(user)).isEqualByComparingTo("0");assertThat(obligations.monthly(user)).isPositive();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM repayment_installments WHERE loan_id=?",Long.class,loan)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transactions WHERE loan_id=?",Long.class,loan)).isEqualTo(1);
    }
    @Test void automaticInsufficientFundsRetryManualRaceAndSettlement() throws Exception {
        long loan=loan("WEEK",3);instant=instant.plus(Duration.ofDays(8));long installment=installment(loan);
        worker.poll();assertThat(jdbc.queryForObject("SELECT paid_at IS NULL FROM repayment_installments WHERE id=?",Boolean.class,installment)).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM repayment_attempts WHERE installment_id=? AND outcome='INSUFFICIENT_FUNDS'",Long.class,installment)).isPositive();
        funded("500");concurrently(()->outcome(()->payments.pay(user,installment,key(),false)),()->outcome(worker::poll));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wallet_entries WHERE user_id=? AND kind='REPAYMENT'",Long.class,user)).isEqualTo(1);
        var quote=payments.settlementQuote(user,loan);assertThat(new BigDecimal((String)quote.get("waivedInterest"))).isPositive();
        payments.settle(user,loan,((Number)quote.get("id")).longValue(),key());
        assertThat(jdbc.queryForObject("SELECT status FROM loans WHERE id=?",String.class,loan)).isEqualTo("SETTLED_EARLY");
        assertThat(obligations.monthly(user)).isEqualByComparingTo("0");reconcile();
    }
    @Test void concurrentPurchasesCannotOverspendAndFailureReplayStaysFailed() throws Exception {
        long product=jdbc.queryForObject("SELECT id FROM products ORDER BY price LIMIT 1",Long.class);
        var row=jdbc.queryForMap("SELECT p.price,s.slug FROM products p JOIN partners s ON s.id=p.partner_id WHERE p.id=?",product);
        funded(((BigDecimal)row.get("price")).toPlainString());var request=new PaymentService.Purchase((String)row.get("slug"),product);
        var results=concurrently(()->outcome(()->payments.purchase(user,key(),request)),()->outcome(()->payments.purchase(user,key(),request)));
        assertThat(results).containsExactlyInAnyOrder("OK","INSUFFICIENT_FUNDS");
        String failedKey=key();assertThat(outcome(()->payments.purchase(user,failedKey,request))).isEqualTo("INSUFFICIENT_FUNDS");
        funded("100");assertThat(outcome(()->payments.purchase(user,failedKey,request))).isEqualTo("INSUFFICIENT_FUNDS");reconcile();
    }
    @Test void staleApprovalsAndSettlementQuotesAreRejected() {
        long first=application("MONTH",6),second=application("MONTH",6);
        long loan=((Number)payments.accept(user,first,key()).get("loanId")).longValue();
        assertThat(outcome(()->payments.accept(user,second,key()))).isEqualTo("INPUTS_CHANGED");
        funded("500");var quote=payments.settlementQuote(user,loan);instant=instant.plusSeconds(301);
        assertThat(outcome(()->payments.settle(user,loan,((Number)quote.get("id")).longValue(),key()))).isEqualTo("QUOTE_EXPIRED");
    }
    @Test void httpOwnershipCsrfValidationAndDecimalStrings() throws Exception {
        mvc.perform(get("/api/wallet").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.balance").value("0.00"));
        mvc.perform(post("/api/wallet/top-ups").session(session).contentType("application/json").content("{\"amount\":\"50.00\"}").header("Idempotency-Key",key())).andExpect(status().isForbidden());
        mvc.perform(postJson("/api/wallet/top-ups","{\"amount\":\"50.00\"}",fetchCsrf()).session(session)).andExpect(status().isBadRequest());
        mvc.perform(postJson("/api/credit-quotes","{\"partnerSlug\":\"markethub\",\"requestedAmount\":100,\"termUnit\":\"MONTH\",\"installmentCount\":1.5}",fetchCsrf()).session(session)).andExpect(status().isBadRequest());
        long loan=loan("MONTH",1);var other=registerAndLogin(uniqueEmail());
        mvc.perform(get("/api/loans/"+loan).session(other)).andExpect(status().isNotFound());
        mvc.perform(postJson("/api/repayments/"+installment(loan)+"/pay","{}",fetchCsrf()).session(other).header("Idempotency-Key",key())).andExpect(status().isNotFound());
        mvc.perform(get("/api/history").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.items").isArray());
        mvc.perform(get("/api/repayments").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].principal").isString());
    }
    @Test void sameDaySettlementChargesOnlyPrincipalAndWorkerDoesNotCollectClosedLoans() {
        long loan=loan("MONTH",12);funded("500");var quote=payments.settlementQuote(user,loan);
        assertThat(quote.get("interest")).isEqualTo("0.00");assertThat(quote.get("total")).isEqualTo("300.00");
        payments.settle(user,loan,((Number)quote.get("id")).longValue(),key());instant=instant.plus(Duration.ofDays(400));worker.poll();
        assertThat(ledger.balance(user)).isEqualByComparingTo("200");reconcile();
    }
    @Test void workerStopsAtOldestInstallmentAndCatchesUpAfterDowntime() {
        long first=loan("WEEK",3),second=loan("MONTH",3);instant=instant.plus(Duration.ofDays(40));
        funded("1");worker.poll();assertThat(jdbc.queryForObject("SELECT count(*) FROM repayment_installments i JOIN loans l ON l.id=i.loan_id WHERE l.user_id=? AND i.paid_at IS NOT NULL",Long.class,user)).isZero();
        funded("1000");worker.poll();assertThat(jdbc.queryForObject("SELECT status FROM loans WHERE id=?",String.class,first)).isEqualTo("PAID");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM repayment_installments WHERE loan_id=? AND paid_at IS NOT NULL",Long.class,second)).isEqualTo(1);reconcile();
    }
    @Test void settlementAndCollectionRaceNeverDoubleDebit() throws Exception {
        long loan=loan("WEEK",3);instant=instant.plus(Duration.ofDays(8));funded("500");
        long installment=installment(loan);var quote=payments.settlementQuote(user,loan);
        var outcomes=concurrently(()->outcome(()->payments.pay(user,installment,key(),false)),
            ()->outcome(()->payments.settle(user,loan,((Number)quote.get("id")).longValue(),key())));
        assertThat(outcomes).allMatch(v->v.equals("OK")||v.equals("QUOTE_EXPIRED"));
        if(outcomes.contains("QUOTE_EXPIRED")) {
            var fresh=payments.settlementQuote(user,loan);
            payments.settle(user,loan,((Number)fresh.get("id")).longValue(),key());
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wallet_entries WHERE loan_id=? AND kind='EARLY_SETTLEMENT'",Long.class,loan)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM repayment_installments WHERE loan_id=? AND paid_at IS NULL",Long.class,loan)).isZero();reconcile();
    }
    @Test void concurrentWorkersOnlyCollectEachInstallmentOnce() throws Exception {
        long loan=loan("WEEK",3);instant=instant.plus(Duration.ofDays(25));funded("500");
        concurrently(()->outcome(worker::poll),()->outcome(worker::poll));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wallet_entries WHERE loan_id=? AND kind='REPAYMENT'",Long.class,loan)).isEqualTo(3);reconcile();
    }
    @Test void purchaseFailureRollsBackDebitAndOperation() {
        funded("500");long product=jdbc.queryForObject("SELECT id FROM products ORDER BY price LIMIT 1",Long.class);
        String slug=jdbc.queryForObject("SELECT s.slug FROM products p JOIN partners s ON s.id=p.partner_id WHERE p.id=?",String.class,product);
        String constraint="test_purchase_failure_"+user;
        jdbc.execute("ALTER TABLE transactions ADD CONSTRAINT "+constraint+" CHECK(user_id<>"+user+" OR payment_method<>'BALANCE')");
        String operationKey=key();
        try {
            assertThatThrownBy(()->payments.purchase(user,operationKey,new PaymentService.Purchase(slug,product))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThat(ledger.balance(user)).isEqualByComparingTo("500");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM financial_operations WHERE user_id=? AND idempotency_key=?",Long.class,user,operationKey)).isZero();
        } finally {jdbc.execute("ALTER TABLE transactions DROP CONSTRAINT "+constraint);}
        payments.purchase(user,operationKey,new PaymentService.Purchase(slug,product));reconcile();
    }
    @Test void rejectionReviewLegacyAndExpiredQuotesCannotCreateLoans() {
        for(String status:List.of("REJECTED","REVIEW")) {
            when(analysis.score(any())).thenReturn(new AnalysisDecision(status,new BigDecimal("0.4"),new BigDecimal("1500"),"USD",List.of(),Map.of(),"rules-v4",false,"NOT_REQUESTED",null,Map.of()));
            assertThat(outcome(()->payments.accept(user,application("MONTH",6),key()))).isEqualTo("NOT_ACTIVATABLE");
        }
        var quote=quotes.create(user,new CreditQuotes.Request("markethub",null,new BigDecimal("300"),"MONTH",6));
        instant=instant.plusSeconds(901);
        assertThat(outcome(()->applications.submit(user,new ApplicationDtos.SubmitRequest("markethub",new BigDecimal("300"),null,false,quote.id()),key()))).isEqualTo("QUOTE_EXPIRED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM loans WHERE user_id=?",Long.class,user)).isZero();
    }
    @Test void approvalCanBeAcceptedAfterPreviewExpiryButNotNextBusinessDay() {
        long app=application("MONTH",6);instant=instant.plusSeconds(901);
        assertThat(outcome(()->payments.accept(user,app,key()))).isEqualTo("OK");
        long next=application("MONTH",6);instant=instant.plus(Duration.ofDays(1));
        assertThat(outcome(()->payments.accept(user,next,key()))).isEqualTo("QUOTE_EXPIRED");
    }
    @Test void optedInFixtureIsIdempotentAndKeepsExistingAccountsIntact() {
        var fixture=new PaymentDemoFixtures(registration,users,jdbc,ledger,payments,pricing,jobs);
        fixture.run(null);fixture.run(null);
        long fixtureUser=users.findByEmail(PaymentDemoFixtures.EMAIL).orElseThrow().getId();
        assertThat(ledger.balance(fixtureUser)).isEqualByComparingTo("25");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM loans WHERE user_id=?",Long.class,fixtureUser)).isEqualTo(1);
        assertThat(new BigDecimal((String)queries.wallet(fixtureUser).get("overdue"))).isPositive();
        assertThat(ledger.balance(user)).isEqualByComparingTo("0");
    }

    @Test void staleCatalogConfirmationCannotChargeAChangedAmount() {
        funded("100");long product=jdbc.queryForObject("SELECT id FROM products ORDER BY price LIMIT 1",Long.class);
        String slug=jdbc.queryForObject("SELECT s.slug FROM products p JOIN partners s ON s.id=p.partner_id WHERE p.id=?",String.class,product);
        assertThat(outcome(()->payments.purchase(user,key(),new PaymentService.Purchase(slug,product,new BigDecimal("999999"))))).isEqualTo("TERMS_CHANGED");
        assertThat(ledger.balance(user)).isEqualByComparingTo("100");reconcile();
    }

}
