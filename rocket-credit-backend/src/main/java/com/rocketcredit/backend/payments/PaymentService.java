// AI-assisted (Claude), 2026: availability checks and acceptance after a human review added; reviewed by the author.
package com.rocketcredit.backend.payments;

import com.rocketcredit.backend.affordability.AffordabilityJobs;
import com.rocketcredit.backend.common.ApiException;
import com.rocketcredit.backend.partners.*;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PaymentService {
    private final JdbcTemplate jdbc; private final PaymentLedger ledger; private final CreditQuotes quotes;
    private final ProductRepository products; private final PartnerRepository partners;
    private final AffordabilityJobs jobs; private final TransactionTemplate tx;
    public PaymentService(JdbcTemplate jdbc,PaymentLedger ledger,CreditQuotes quotes,ProductRepository products,PartnerRepository partners,AffordabilityJobs jobs,TransactionTemplate tx) {
        this.jdbc=jdbc;this.ledger=ledger;this.quotes=quotes;this.products=products;this.partners=partners;this.jobs=jobs;this.tx=tx;
    }
    public Map<String,Object> topUp(long user,String key,BigDecimal amount) {
        var value=RepaymentCalculator.positive(amount);
        return ledger.execute(user,key,"TOP_UP",Map.of("amount",value.toPlainString()),op->{
            long entry=ledger.post(user,op,"TOP_UP",value,null,"Demo top-up");
            return result(user,"entryId",entry);
        });
    }
    public record Purchase(String partnerSlug,Long productId,BigDecimal expectedAmount){
        public Purchase(String partnerSlug,Long productId){this(partnerSlug,productId,null);}
    }
    public Map<String,Object> purchase(long user,String key,Purchase request) {
        return ledger.execute(user,key,"PURCHASE",request,op->{
            var partner=Availability.require(partners.findBySlug(request.partnerSlug()==null?"":request.partnerSlug()).orElseThrow(()->ApiException.badRequest("UNKNOWN_PARTNER","Unknown store.")));
            if(request.productId()==null)throw ApiException.badRequest("UNKNOWN_PRODUCT","Choose a catalog product.");
            var product=Availability.require(products.findById(request.productId()).filter(p->p.getPartnerId().equals(partner.getId())).orElseThrow(()->ApiException.badRequest("UNKNOWN_PRODUCT","Product not found for this store.")));
            if(request.expectedAmount()!=null && request.expectedAmount().compareTo(product.getPrice())!=0)
                throw ApiException.conflict("TERMS_CHANGED","The catalog price changed. Reload the store and review the purchase again.");
            BigDecimal shortfall=product.getPrice().subtract(ledger.balance(user));
            if(shortfall.signum()>0)return PaymentLedger.insufficient(shortfall);
            long entry=ledger.post(user,op,"PURCHASE",product.getPrice().negate(),null,product.getName());
            long purchase=recordPurchase(user,partner.getId(),product.getId(),product.getPrice(),product.getName(),"BALANCE",null,entry);
            jobs.recalculate(user); // purchase history changed: invalidate outstanding approvals
            var response=new LinkedHashMap<>(result(user,"purchaseId",purchase));response.put("chargedAmount",product.getPrice().toPlainString());return response;
        });
    }
    public Map<String,Object> accept(long user,long application,String key) {
        return ledger.execute(user,key,"ACCEPT",Map.of("applicationId",application),op->{
            var apps=jdbc.queryForList("SELECT * FROM credit_applications WHERE id=? AND user_id=?",application,user);
            if(apps.isEmpty())throw ApiException.notFound("Application");
            var app=apps.getFirst();
            var existing=jdbc.queryForList("SELECT id FROM loans WHERE application_id=?",application);
            if(!existing.isEmpty())return result(user,"loanId",existing.getFirst().get("id"));
            // An automated APPROVED, or a REVIEW that an administrator approved (human intervention); the
            // automated decision row itself is never changed, the human outcome lives in application_reviews.
            BigDecimal reviewed=jdbc.query("SELECT approved_amount FROM application_reviews WHERE application_id=? AND outcome='APPROVED'",
                (rs,n)->rs.getBigDecimal(1),application).stream().findFirst().orElse(null);
            boolean offer="APPROVED".equals(app.get("decision_status")) || ("REVIEW".equals(app.get("decision_status")) && reviewed!=null);
            if(!offer || app.get("quote_id")==null)
                throw ApiException.conflict("NOT_ACTIVATABLE","This application has no active credit offer.");
            var quote=quotes.get(user,((Number)app.get("quote_id")).longValue());
            if(reviewed!=null)quote=quotes.reissueForReview(user,quote,reviewed); else quotes.validate(user,quote,true);
            var schedule=quote.schedule();
            long loan=jdbc.queryForObject("""
                INSERT INTO loans(user_id,application_id,quote_id,partner_id,product_id,principal,term_unit,installment_count,annual_rate,pricing_version,start_date,created_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?) RETURNING id
                """,Long.class,user,application,quote.id(),quote.partnerId(),quote.productId(),new BigDecimal(schedule.principal()),schedule.termUnit(),schedule.installmentCount(),new BigDecimal(schedule.annualRate()),schedule.pricingVersion(),schedule.startDate(),ledger.now());
            for(var part:schedule.installments())jdbc.update("INSERT INTO repayment_installments(loan_id,sequence,period_start,due_date,principal,interest) VALUES(?,?,?,?,?,?)",
                loan,part.sequence(),part.periodStart(),part.dueDate(),new BigDecimal(part.principal()),new BigDecimal(part.interest()));
            String description=quote.productId()==null?"Financed store purchase":products.findById(quote.productId()).orElseThrow().getName();
            recordPurchase(user,quote.partnerId(),quote.productId(),new BigDecimal(schedule.principal()),description,"CREDIT",loan,null);
            jobs.recalculate(user);
            return result(user,"loanId",loan);
        });
    }
    private long recordPurchase(long user,long partner,Long product,BigDecimal amount,String description,String method,Long loan,Long entry) {
        return jdbc.queryForObject("""
            INSERT INTO transactions(user_id,partner_id,product_id,amount,occurred_on,status,paid_on_time,description,origin,payment_method,loan_id,wallet_entry_id,created_at)
            VALUES(?,?,?,?,?,'COMPLETED',true,?,'DEMO_PURCHASE',?,?,?,?) RETURNING id
            """,Long.class,user,partner,product,amount,ledger.today(),description,method,loan,entry,ledger.now());
    }
    public Map<String,Object> pay(long user,long installment,String key,boolean automatic) {
        return ledger.execute(user,key,"REPAYMENT",Map.of("installmentId",installment),op->{
            var owned=jdbc.queryForList("SELECT l.id FROM loans l JOIN repayment_installments i ON i.loan_id=l.id WHERE i.id=? AND l.user_id=?",installment,user);
            if(owned.isEmpty())throw ApiException.notFound("Installment");
            long loan=((Number)owned.getFirst().get("id")).longValue(); lockLoan(user,loan);
            var rows=jdbc.queryForList("SELECT * FROM repayment_installments WHERE loan_id=? ORDER BY sequence FOR UPDATE",loan);
            var row=rows.stream().filter(r->((Number)r.get("id")).longValue()==installment).findFirst().orElseThrow();
            if(row.get("paid_at")!=null)return result(user,"entryId",row.get("payment_entry_id"));
            var first=rows.stream().filter(r->r.get("paid_at")==null).findFirst().orElseThrow();
            if(!first.get("id").equals(row.get("id")) || date(row,"due_date").isAfter(ledger.today()))
                throw ApiException.conflict("INSTALLMENT_NOT_DUE","Only the oldest due installment for this credit can be paid.");
            if(automatic) {
                var oldest=jdbc.queryForObject("SELECT i.id FROM repayment_installments i JOIN loans l ON l.id=i.loan_id WHERE l.user_id=? AND i.paid_at IS NULL AND i.due_date<=? ORDER BY i.due_date,l.id,i.sequence LIMIT 1",Long.class,user,ledger.today());
                if(oldest!=installment)throw ApiException.conflict("INSTALLMENT_NOT_DUE","An older payment must be collected first.");
            }
            BigDecimal total=decimal(row,"principal").add(decimal(row,"interest"));
            BigDecimal shortfall=total.subtract(ledger.balance(user));
            if(shortfall.signum()>0){attempt(installment,op,automatic,"INSUFFICIENT_FUNDS");return PaymentLedger.insufficient(shortfall);}
            long entry=ledger.post(user,op,"REPAYMENT",total.negate(),loan,"Credit #"+loan+" installment "+row.get("sequence"));
            jdbc.update("UPDATE repayment_installments SET principal_paid=principal,interest_paid=interest,payment_entry_id=?,paid_at=? WHERE id=?",entry,ledger.now(),installment);
            boolean complete=rows.stream().filter(r->r.get("paid_at")==null).count()==1;
            jdbc.update("UPDATE loans SET version=version+1,status=?,closed_at=? WHERE id=?",complete?"PAID":"ACTIVE",complete?ledger.now():null,loan);
            attempt(installment,op,automatic,"PAID");jobs.recalculate(user);
            return result(user,"entryId",entry);
        });
    }
    private void attempt(long installment,long op,boolean auto,String outcome){jdbc.update("INSERT INTO repayment_attempts(installment_id,operation_id,mode,outcome,created_at) VALUES(?,?,?,?,?)",installment,op,auto?"AUTOMATIC":"MANUAL",outcome,ledger.now());}
    public Map<String,Object> settlementQuote(long user,long loan) {
        ledger.ensure(user);
        return tx.execute(status->{
            ledger.lock(user);var current=lockLoan(user,loan);
            if(!"ACTIVE".equals(current.get("status")))throw ApiException.conflict("LOAN_CLOSED","This credit is already closed.");
            var rows=jdbc.queryForList("SELECT * FROM repayment_installments WHERE loan_id=? AND paid_at IS NULL ORDER BY sequence FOR UPDATE",loan);
            BigDecimal principal=BigDecimal.ZERO,earned=BigDecimal.ZERO,scheduled=BigDecimal.ZERO;
            List<Map<String,Object>> allocations=new ArrayList<>();
            for(var row:rows) {
                BigDecimal interest=decimal(row,"interest");
                BigDecimal accrued=RepaymentCalculator.earned(interest,date(row,"period_start"),date(row,"due_date"),ledger.today());
                principal=principal.add(decimal(row,"principal"));earned=earned.add(accrued);scheduled=scheduled.add(interest);
                allocations.add(Map.of("id",row.get("id"),"interest",accrued.toPlainString()));
            }
            earned=RepaymentCalculator.money(earned);var expiry=ledger.expires(5);
            long id=jdbc.queryForObject("INSERT INTO settlement_quotes(user_id,loan_id,loan_version,as_of,expires_at,principal,interest,waived,allocations,created_at) VALUES(?,?,?,?,?,?,?,?,?::jsonb,?) RETURNING id",
                Long.class,user,loan,current.get("version"),ledger.today(),expiry,principal,earned,scheduled.subtract(earned),ledger.json(allocations),ledger.now());
            return Map.of("id",id,"loanId",loan,"principal",principal.toPlainString(),"interest",earned.toPlainString(),"waivedInterest",scheduled.subtract(earned).toPlainString(),"total",principal.add(earned).toPlainString(),"expiresAt",expiry);
        });
    }
    public Map<String,Object> settle(long user,long loan,long quoteId,String key) {
        return ledger.execute(user,key,"SETTLEMENT",Map.of("loanId",loan,"quoteId",quoteId),op->{
            var current=lockLoan(user,loan);
            var quotes=jdbc.queryForList("SELECT *,expires_at::text AS expiry FROM settlement_quotes WHERE id=? AND loan_id=? AND user_id=?",quoteId,loan,user);
            if(quotes.isEmpty())throw ApiException.notFound("Settlement quote");
            var quote=quotes.getFirst();
            OffsetDateTime expires=jdbc.queryForObject("SELECT expires_at FROM settlement_quotes WHERE id=?",OffsetDateTime.class,quoteId);
            if(!ledger.now().isBefore(expires) || !quote.get("loan_version").equals(current.get("version")) || !"ACTIVE".equals(current.get("status")))
                throw ApiException.conflict("QUOTE_EXPIRED","The payoff changed. Review a new settlement quote.");
            BigDecimal total=decimal(quote,"principal").add(decimal(quote,"interest"));
            BigDecimal shortfall=total.subtract(ledger.balance(user));if(shortfall.signum()>0)return PaymentLedger.insufficient(shortfall);
            var rows=jdbc.queryForList("SELECT * FROM repayment_installments WHERE loan_id=? AND paid_at IS NULL ORDER BY sequence FOR UPDATE",loan);
            long entry=ledger.post(user,op,"EARLY_SETTLEMENT",total.negate(),loan,"Early settlement of credit #"+loan);
            BigDecimal remainingInterest=decimal(quote,"interest");
            for(var row:rows) {
                BigDecimal interest=decimal(row,"interest");
                // Only one unfinished period can have fractional earned interest. Allocate aggregate cents in order.
                BigDecimal earned=RepaymentCalculator.money(RepaymentCalculator.earned(interest,date(row,"period_start"),date(row,"due_date"),ledger.today())).min(remainingInterest);
                remainingInterest=remainingInterest.subtract(earned);
                jdbc.update("UPDATE repayment_installments SET principal_paid=principal,interest_paid=?,interest_waived=?,payment_entry_id=?,paid_at=? WHERE id=?",
                    earned,interest.subtract(earned),entry,ledger.now(),row.get("id"));
            }
            if(remainingInterest.signum()!=0)throw new IllegalStateException("Settlement allocation mismatch");
            jdbc.update("UPDATE loans SET status='SETTLED_EARLY',version=version+1,closed_at=? WHERE id=?",ledger.now(),loan);jobs.recalculate(user);
            return result(user,"entryId",entry);
        });
    }
    private Map<String,Object> lockLoan(long user,long loan) {
        return jdbc.queryForList("SELECT * FROM loans WHERE id=? AND user_id=? FOR UPDATE",loan,user).stream().findFirst().orElseThrow(()->ApiException.notFound("Loan"));
    }
    private Map<String,Object> result(long user,String name,Object id){return Map.of(name,id,"balance",ledger.balance(user).toPlainString(),"currency","USD");}
    static BigDecimal decimal(Map<String,Object> row,String name){return (BigDecimal)row.get(name);}
    static LocalDate date(Map<String,Object> row,String name){return ((java.sql.Date)row.get(name)).toLocalDate();}
}
