package com.rocketcredit.backend.payments;

import static org.assertj.core.api.Assertions.*;
import com.rocketcredit.backend.common.ApiException;
import java.math.*;
import java.time.*;
import org.junit.jupiter.api.Test;

class RepaymentCalculatorTest {
    private final RepaymentCalculator pricing=new RepaymentCalculator(new BigDecimal("0.12"));
    @Test void boundariesDatesAndPrincipalAreExact() {
        for(String unit:new String[]{"WEEK","MONTH"})for(int count=1;count<=(unit.equals("WEEK")?3:24);count++) {
            var s=pricing.calculate(new BigDecimal("1200.00"),unit,count,LocalDate.of(2028,1,31));
            assertThat(s.installments()).hasSize(count);
            assertThat(s.installments().stream().map(p->new BigDecimal(p.principal())).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo("1200");
            assertThat(s.installments().stream().map(p->new BigDecimal(p.total())).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo(s.totalRepayment());
            assertThat(s.installments().getFirst().dueDate()).isAfter(s.startDate());
        }
        var s=pricing.calculate(new BigDecimal("1200"),"MONTH",3,LocalDate.of(2028,1,31));
        assertThat(s.installments().get(0).dueDate()).isEqualTo(LocalDate.of(2028,2,29));
        assertThat(s.installments().get(1).dueDate()).isEqualTo(LocalDate.of(2028,3,31));
        var nonLeap=pricing.calculate(new BigDecimal("1200"),"MONTH",3,LocalDate.of(2027,1,31));
        assertThat(nonLeap.installments().getFirst().dueDate()).isEqualTo(LocalDate.of(2027,2,28));
    }
    @Test void zeroRateFinalRoundingAndInvalidTerms() {
        var s=new RepaymentCalculator(BigDecimal.ZERO).calculate(new BigDecimal("100"),"MONTH",3,LocalDate.of(2026,1,1));
        assertThat(s.installments()).extracting(RepaymentCalculator.Part::total).containsExactly("33.33","33.33","33.34");
        assertThat(s.totalInterest()).isEqualTo("0.00");
        for(String unit:new String[]{"WEEK","MONTH","DAY"})for(int count:new int[]{0,25})
            assertThatThrownBy(()->pricing.calculate(BigDecimal.TEN,unit,count,LocalDate.now())).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->pricing.calculate(BigDecimal.TEN,"WEEK",4,LocalDate.now())).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->pricing.calculate(new BigDecimal("0.01"),"MONTH",24,LocalDate.now())).isInstanceOf(ApiException.class);
    }
    @Test void earnedInterestUsesCalendarDaysAndNeverAddsLateFees() {
        var start=LocalDate.of(2026,3,1);var due=LocalDate.of(2026,4,1);var interest=new BigDecimal("31.00");
        assertThat(RepaymentCalculator.earned(interest,start,due,start)).isEqualByComparingTo("0");
        assertThat(RepaymentCalculator.earned(interest,start,due,start.plusDays(15))).isEqualByComparingTo("15");
        assertThat(RepaymentCalculator.earned(interest,start,due,due)).isEqualByComparingTo("31");
        assertThat(RepaymentCalculator.earned(interest,start,due,due.plusDays(100))).isEqualByComparingTo("31");
    }
    @Test void sharedGoldenCases() throws Exception {
        var path=java.nio.file.Path.of("../research/fixtures/repayment-golden.json");
        var cases=new com.fasterxml.jackson.databind.ObjectMapper().readTree(path.toFile());
        for(var c:cases) {
            var calculator=new RepaymentCalculator(new BigDecimal(c.path("annualRate").asText()));
            var s=calculator.calculate(new BigDecimal(c.path("principal").asText()),c.path("termUnit").asText(),c.path("installmentCount").asInt(),LocalDate.of(2026,1,31));
            assertThat(s.installments().stream().map(p->new BigDecimal(p.total())).max(BigDecimal::compareTo).orElseThrow()).isEqualByComparingTo(c.path("maximumPayment").asText());
            assertThat(s.totalInterest()).isEqualTo(c.path("totalInterest").asText());
        }
    }
}
