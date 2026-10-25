package com.rocketcredit.backend.payments;

import com.rocketcredit.backend.common.ApiException;
import com.rocketcredit.backend.partners.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class CreditQuotes {
    private final JdbcTemplate jdbc; private final PaymentLedger ledger; private final RepaymentCalculator calculator;
    private final PartnerRepository partners; private final ProductRepository products; private final TransactionTemplate tx;
    public CreditQuotes(JdbcTemplate jdbc,PaymentLedger ledger,RepaymentCalculator calculator,PartnerRepository partners,ProductRepository products,TransactionTemplate tx) {
        this.jdbc=jdbc;this.ledger=ledger;this.calculator=calculator;this.partners=partners;this.products=products;this.tx=tx;
    }
    public record Request(String partnerSlug,Long productId,BigDecimal requestedAmount,String termUnit,int installmentCount){}
    public record Quote(long id,long partnerId,Long productId,long generation,OffsetDateTime expiresAt,RepaymentCalculator.Schedule schedule) {}
    public Quote create(long user,Request req) {
        ledger.ensure(user);
        var partner=partners.findBySlug(req.partnerSlug()==null?"":req.partnerSlug()).orElseThrow(()->ApiException.badRequest("UNKNOWN_PARTNER","Unknown store."));
        BigDecimal amount=req.requestedAmount();
        if(req.productId()!=null) {
            var product=products.findById(req.productId()).filter(p->p.getPartnerId().equals(partner.getId())).orElseThrow(()->ApiException.badRequest("UNKNOWN_PRODUCT","Product not found for this store."));
            if(amount!=null && amount.compareTo(product.getPrice())!=0) throw ApiException.badRequest("AMOUNT_MISMATCH","Amount must match the catalog price.");
            amount=product.getPrice();
        }
        var schedule=calculator.calculate(amount,req.termUnit(),req.installmentCount(),ledger.today());
        return tx.execute(status->{
            long generation=ledger.lock(user); var expiry=ledger.expires(15);
            long id=jdbc.queryForObject("""
                INSERT INTO credit_quotes(user_id,partner_id,product_id,principal,term_unit,installment_count,annual_rate,pricing_version,generation,start_date,expires_at,schedule,created_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?::jsonb,?) RETURNING id
                """,Long.class,user,partner.getId(),req.productId(),new BigDecimal(schedule.principal()),req.termUnit(),req.installmentCount(),calculator.rate(),calculator.version(),generation,schedule.startDate(),expiry,ledger.json(schedule),ledger.now());
            return new Quote(id,partner.getId(),req.productId(),generation,expiry,schedule);
        });
    }
    public Quote get(long user,long id) {
        return jdbc.query("SELECT * FROM credit_quotes WHERE id=? AND user_id=?",(rs,n)->new Quote(rs.getLong("id"),rs.getLong("partner_id"),rs.getObject("product_id",Long.class),rs.getLong("generation"),rs.getObject("expires_at",OffsetDateTime.class),ledger.read(rs.getString("schedule"),RepaymentCalculator.Schedule.class)),id,user)
            .stream().findFirst().orElseThrow(()->ApiException.notFound("Quote"));
    }
    public void validate(long user,Quote q,boolean accepting) {
        if(!q.schedule().startDate().equals(ledger.today()) || (!accepting && !ledger.now().isBefore(q.expiresAt())))
            throw ApiException.conflict("QUOTE_EXPIRED","The quote expired. Review a fresh schedule and submit again.");
        if(!calculator.version().equals(q.schedule().pricingVersion())) throw ApiException.conflict("TERMS_CHANGED","Pricing changed. Review a fresh quote.");
        Long generation=jdbc.queryForObject("SELECT generation FROM financial_input_state WHERE user_id=?",Long.class,user);
        if(generation!=q.generation()) throw ApiException.conflict("INPUTS_CHANGED","Financial information or obligations changed. Submit a fresh request.");
        if(accepting && new BigDecimal(q.schedule().principal()).compareTo(partners.findById(q.partnerId()).orElseThrow(()->ApiException.notFound("Store")).getAmountCap())>0)
            throw ApiException.conflict("TERMS_CHANGED","The store financing limit changed. Review a fresh quote.");
        if(q.productId()!=null) {
            var product=products.findById(q.productId()).orElseThrow(()->ApiException.conflict("TERMS_CHANGED","Product is no longer available."));
            if(product.getPrice().compareTo(new BigDecimal(q.schedule().principal()))!=0) throw ApiException.conflict("TERMS_CHANGED","The catalog price changed. Review a fresh quote.");
        }
    }
    public RepaymentCalculator.Terms terms(Quote q) {return new RepaymentCalculator.Terms(q.schedule().termUnit(),q.schedule().installmentCount(),new BigDecimal(q.schedule().annualRate()),q.schedule().pricingVersion());}
}
