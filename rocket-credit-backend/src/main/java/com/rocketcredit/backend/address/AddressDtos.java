package com.rocketcredit.backend.address;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public final class AddressDtos {
    private AddressDtos() {}

    public record SaveRequest(@NotNull Integer expectedRevision,
                              @NotBlank @Size(max = 2) String countryCode,
                              @NotBlank @Size(max = 100) String city,
                              @NotBlank @Size(max = 40) String districtId,
                              @NotBlank @Size(max = 20) String postalCode,
                              @NotBlank @Size(max = 160) String street,
                              @NotBlank @Size(max = 40) String building,
                              @Size(max = 40) String unit) {}

    public record Current(boolean available, int revision, String countryCode, String city, String districtId,
                          String postalCode, String street, String building, String unit,
                          String verificationStatus, OffsetDateTime verifiedAt) {}
    public record SaveResult(Current address, long generation, long recalculationJobId) {}

    public record District(String districtId, String displayName, String city, String countryCode,
                           String datasetVersion, java.math.BigDecimal monthlyRent,
                           java.math.BigDecimal monthlyGroceries, java.math.BigDecimal citySalaryMonthly,
                           String salaryBasis, String currency, String sourceLabel, LocalDate observedAt,
                           boolean synthetic) {}
    public record Verification(long id, int addressRevision, String status, String scenarioId,
                              String dataSource, List<String> checks, OffsetDateTime createdAt,
                              long recalculationJobId) {}
}
