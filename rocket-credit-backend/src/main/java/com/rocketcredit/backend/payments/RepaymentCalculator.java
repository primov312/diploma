package com.rocketcredit.backend.payments;

import com.rocketcredit.backend.common.ApiException;
import java.math.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Pure decimal pricing. Dates always derive from the original anchor, never the previous due date. */
@Component
public class RepaymentCalculator {
    public static final ZoneId ZONE = ZoneId.of("Europe/Budapest");
    public static final MathContext MC = MathContext.DECIMAL128;
    public static final BigDecimal MAX = new BigDecimal("9999999999.99");
    private final BigDecimal rate;
    public RepaymentCalculator(@Value("${rocket.payments.annual-rate:0.12}") BigDecimal rate) {
        if (rate.signum()<0 || rate.compareTo(BigDecimal.ONE)>0 || rate.scale()>6) throw new IllegalArgumentException("Invalid annual rate");
        this.rate=rate;
    }
    public BigDecimal rate() { return rate; }
    public String version() { return "annuity-v1-"+rate.stripTrailingZeros().toPlainString(); }
    public record Terms(String termUnit, int installmentCount, BigDecimal annualRate, String pricingVersion) {}
    public Terms terms(String unit,int count) { validateTerm(unit,count); return new Terms(unit,count,rate,version()); }
    public record Part(int sequence, LocalDate periodStart, LocalDate dueDate, String principal, String interest, String total) {}
    public record Schedule(String principal, String annualRate, String termUnit, int installmentCount, String pricingVersion,
                           LocalDate startDate, String totalInterest, String totalRepayment, List<Part> installments) {}
    public static BigDecimal money(BigDecimal value) { return value.setScale(2,RoundingMode.HALF_UP); }
    public static BigDecimal positive(BigDecimal value) {
        if(value==null || value.signum()<=0 || value.compareTo(MAX)>0 || value.stripTrailingZeros().scale()>2)
            throw ApiException.badRequest("INVALID_AMOUNT","Enter a positive USD amount with at most two decimal places.");
        return money(value);
    }
    public static void validateTerm(String unit,int count) {
        if(!("WEEK".equals(unit) && count>=1 && count<=3) && !("MONTH".equals(unit) && count>=1 && count<=24))
            throw ApiException.badRequest("INVALID_REPAYMENT_TERM","Choose 1–3 weeks or 1–24 months.");
    }
    public Schedule calculate(BigDecimal principal,String unit,int count,LocalDate start) {
        principal=positive(principal); validateTerm(unit,count);
        BigDecimal r=rate.divide(BigDecimal.valueOf("WEEK".equals(unit)?52:12),MC);
        BigDecimal payment=money(r.signum()==0 ? principal.divide(BigDecimal.valueOf(count),MC)
            : principal.multiply(r,MC).divide(BigDecimal.ONE.subtract(BigDecimal.ONE.divide(BigDecimal.ONE.add(r).pow(count,MC),MC)),MC));
        BigDecimal remaining=principal,totalInterest=BigDecimal.ZERO;
        List<Part> parts=new ArrayList<>(); LocalDate previous=start;
        for(int i=1;i<=count;i++) {
            BigDecimal interest=money(remaining.multiply(r,MC));
            BigDecimal capital=i==count?remaining:payment.subtract(interest);
            if(capital.signum()<=0 || capital.compareTo(remaining)>0 || (i<count && capital.compareTo(remaining)==0))
                throw ApiException.badRequest("INVALID_AMOUNT","This amount is too small for the selected number of installments.");
            LocalDate due="WEEK".equals(unit)?start.plusWeeks(i):start.plusMonths(i);
            parts.add(new Part(i,previous,due,capital.toPlainString(),interest.toPlainString(),capital.add(interest).toPlainString()));
            totalInterest=totalInterest.add(interest); remaining=remaining.subtract(capital); previous=due;
        }
        if(principal.add(totalInterest).compareTo(MAX)>0) throw ApiException.badRequest("INVALID_AMOUNT","Total repayment exceeds the supported amount.");
        return new Schedule(principal.toPlainString(),rate.toPlainString(),unit,count,version(),start,
            money(totalInterest).toPlainString(),principal.add(totalInterest).toPlainString(),List.copyOf(parts));
    }
    /** No penalty interest. Each original period earns its scheduled interest linearly by calendar day. */
    public static BigDecimal earned(BigDecimal interest,LocalDate start,LocalDate due,LocalDate today) {
        long days=ChronoUnit.DAYS.between(start,due);
        long elapsed=Math.max(0,Math.min(days,ChronoUnit.DAYS.between(start,today)));
        return interest.multiply(BigDecimal.valueOf(elapsed)).divide(BigDecimal.valueOf(days),MC);
    }
}
