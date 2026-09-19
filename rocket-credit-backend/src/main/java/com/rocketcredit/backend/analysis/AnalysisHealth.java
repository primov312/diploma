package com.rocketcredit.backend.analysis;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AnalysisHealth(String status, String policyVersion, String modelVersion, boolean modelLoaded) {}
