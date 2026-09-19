package com.rocketcredit.backend.analysis;

/**
 * The analysis service could not produce a result (unreachable, timeout,
 * 5xx, or rejected our request). Callers turn this into a technical error;
 * it is never a credit rejection and nothing is saved.
 */
public class AnalysisUnavailableException extends RuntimeException {
    public AnalysisUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public AnalysisUnavailableException(String message) {
        super(message);
    }
}
