package com.rocketcredit.backend.payments;

import com.rocketcredit.backend.auth.RegistrationService;
import com.rocketcredit.backend.affordability.AffordabilityJobs;
import com.rocketcredit.backend.users.UserRepository;
import java.math.BigDecimal;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Explicit opt-in synthetic scenario. Never modifies existing customers or converts historical approvals. */
@Component
@ConditionalOnProperty(name="rocket.payments.seed-fixtures",havingValue="true")
public class PaymentDemoFixtures implements ApplicationRunner {
    public static final String EMAIL="repayments@demo.rocket.local";
    private final RegistrationService registration;private final UserRepository users;private final JdbcTemplate jdbc;
    private final PaymentLedger ledger;private final PaymentService payments;private final RepaymentCalculator pricing;private final AffordabilityJobs jobs;
    public PaymentDemoFixtures(RegistrationService registration,UserRepository users,JdbcTemplate jdbc,PaymentLedger ledger,PaymentService payments,RepaymentCalculator pricing,AffordabilityJobs jobs){
        this.registration=registration;this.users=users;this.jdbc=jdbc;this.ledger=ledger;this.payments=payments;this.pricing=pricing;this.jobs=jobs;
    }
    @Override @Transactional public void run(ApplicationArguments args) {
        if(users.findByEmail(EMAIL).isPresent())return;
        long user=registration.register(EMAIL,"rocket-demo-123","Synthetic Repayment Demo").getId();
        ledger.ensure(user);long generation=ledger.lock(user);
        long partner=jdbc.queryForObject("SELECT id FROM partners WHERE slug='markethub'",Long.class);
        var start=ledger.today().minusMonths(2);var schedule=pricing.calculate(new BigDecimal("300"),"MONTH",6,start);
        long quote=jdbc.queryForObject("""
            INSERT INTO credit_quotes(user_id,partner_id,principal,term_unit,installment_count,annual_rate,pricing_version,generation,start_date,expires_at,schedule,created_at)
            VALUES(?, ?,300,'MONTH',6,?,?,?,?,?,?::jsonb,?) RETURNING id
            """,Long.class,user,partner,pricing.rate(),pricing.version(),generation,start,ledger.now(),ledger.json(schedule),ledger.now());
        long application=jdbc.queryForObject("""
            INSERT INTO credit_applications(user_id,partner_id,requested_amount,decision_status,score,possible_amount,reasons,factors,feature_snapshot,ai_requested,ai_status,policy_version,observed_at,quote_id)
            VALUES(?,?,300,'APPROVED',1,300,'["SYNTHETIC_PAYMENT_FIXTURE"]'::jsonb,'{}'::jsonb,'{"source":"SYNTHETIC_PAYMENT_FIXTURE"}'::jsonb,false,'NOT_REQUESTED','fixture-v1',?,?) RETURNING id
            """,Long.class,user,partner,ledger.now(),quote);
        long loan=jdbc.queryForObject("""
            INSERT INTO loans(user_id,application_id,quote_id,partner_id,principal,term_unit,installment_count,annual_rate,pricing_version,start_date)
            VALUES(?,?,?,?,300,'MONTH',6,?,?,?) RETURNING id
            """,Long.class,user,application,quote,partner,pricing.rate(),pricing.version(),start);
        for(var part:schedule.installments())jdbc.update("INSERT INTO repayment_installments(loan_id,sequence,period_start,due_date,principal,interest) VALUES(?,?,?,?,?,?)",loan,part.sequence(),part.periodStart(),part.dueDate(),new BigDecimal(part.principal()),new BigDecimal(part.interest()));
        jdbc.update("""
            INSERT INTO transactions(user_id,partner_id,fixture_id,amount,occurred_on,status,paid_on_time,description,origin,payment_method,loan_id)
            VALUES(?,?,'payment-demo-purchase-v1',300,?,'COMPLETED',true,'[Synthetic payment fixture] Store financing','DEMO_PURCHASE','CREDIT',?)
            """,user,partner,start,loan);
        payments.topUp(user,"payment-demo-topup-v1",new BigDecimal("25"));
        jobs.recalculate(user);
    }
}
