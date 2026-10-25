package com.rocketcredit.backend.address;

import java.time.OffsetDateTime;
import java.util.List;

public final class HousingDtos {
    private HousingDtos() {}

    public record Attempt(long id, String claimedSituation, String status, List<String> checks, OffsetDateTime createdAt) {}

    /** {@code verified}: the current situation was set by a change whose document passed the checks. */
    public record Current(String housingSituation, int financialRevision, boolean verified, OffsetDateTime verifiedAt,
                          Attempt lastAttempt) {}

    public record Result(long id, String status, String claimedSituation, String housingSituation, int financialRevision,
                         String scenarioId, String dataSource, List<String> checks, OffsetDateTime createdAt) {}
}
