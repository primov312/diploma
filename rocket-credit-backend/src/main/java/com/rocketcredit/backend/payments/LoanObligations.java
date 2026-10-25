package com.rocketcredit.backend.payments;

import java.math.*;
import java.time.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class LoanObligations {
    private final JdbcTemplate jdbc; private final Clock clock;
    public LoanObligations(JdbcTemplate jdbc,Clock clock){this.jdbc=jdbc;this.clock=clock;}
    public BigDecimal monthly(long user) {
        return monthly(user,LocalDate.now(clock.withZone(RepaymentCalculator.ZONE)));
    }
    public BigDecimal monthly(long user,LocalDate asOf) {
        LocalDate through=asOf.plusMonths(1);
        return jdbc.queryForObject("""
            SELECT COALESCE(sum(greatest(regular,due)),0) FROM (
              SELECT l.id,
                max(i.principal+i.interest)*(CASE WHEN l.term_unit='WEEK' THEN 52.0/12 ELSE 1 END) regular,
                COALESCE(sum(i.principal+i.interest) FILTER(WHERE i.paid_at IS NULL AND i.due_date<=?),0) due
              FROM loans l JOIN repayment_installments i ON i.loan_id=l.id
              WHERE l.user_id=? AND l.status='ACTIVE' GROUP BY l.id
            ) obligations
            """,BigDecimal.class,through,user).setScale(2,RoundingMode.UP);
    }
}
