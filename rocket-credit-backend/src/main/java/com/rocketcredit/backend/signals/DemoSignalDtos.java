package com.rocketcredit.backend.signals;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

public final class DemoSignalDtos {
    private DemoSignalDtos() {}
    public record Settings(boolean locationEnabled, boolean socialEnabled, long permissionGeneration) {}
    public record UpdateRequest(@NotNull Boolean locationEnabled, @NotNull Boolean socialEnabled) {}
    public record RunRequest(@jakarta.validation.constraints.NotBlank String scenarioId) {}
    public record RunAccepted(long jobId, String state, JsonNode report) {}
}
