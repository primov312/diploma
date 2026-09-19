package com.rocketcredit.backend.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/** Uniform error body: {"error":"CODE","message":"...","fields":{...}} */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(String error, String message, Map<String, String> fields) {
    public static ApiError of(String error, String message) {
        return new ApiError(error, message, null);
    }
}
