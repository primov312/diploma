package com.rocketcredit.backend.users;

public record FinancialInputsSaveResult(FinancialInputsDto inputs, long generation, long recalculationJobId) {}
