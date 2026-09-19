package com.rocketcredit.backend.applications.features;

/** A required feature task failed, timed out or could not be scheduled. Technical error, never a rejection. */
public class FeaturePreparationException extends RuntimeException {
    public FeaturePreparationException(String message, Throwable cause) {
        super(message, cause);
    }
}
