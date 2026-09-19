package com.rocketcredit.backend.fixtures;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Shape of src/main/resources/fixtures/demo-customers.json. */
public record DemoFixtures(LocalDate anchorDate, String password, List<Customer> customers) {

    public record Customer(String fixtureId, String email, String displayName, int accountAgeMonths,
                           Finance finance, List<History> history) {}

    public record Finance(BigDecimal monthlyIncome, BigDecimal monthlyExpenses, BigDecimal monthlyObligations,
                          boolean profileComplete, boolean emailVerified) {}

    /** One partner's purchase sequence: {@code count} purchases every {@code everyDays} days ending at the anchor date. */
    public record History(String partner, int count, int everyDays, List<BigDecimal> amounts,
                          List<Integer> refunded, List<Integer> late) {}
}
