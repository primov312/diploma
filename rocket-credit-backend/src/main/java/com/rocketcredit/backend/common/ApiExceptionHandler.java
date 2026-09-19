package com.rocketcredit.backend.common;

import com.rocketcredit.backend.analysis.AnalysisUnavailableException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> api(ApiException e) {
        return ResponseEntity.status(e.getStatus()).body(ApiError.of(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(f -> fields.putIfAbsent(f.getField(), f.getDefaultMessage()));
        // Field names only; rejected values are not echoed (they may include a password).
        return ResponseEntity.badRequest().body(new ApiError("VALIDATION_FAILED", "Request is invalid", fields));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> unreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(ApiError.of("MALFORMED_REQUEST", "Request body could not be read"));
    }

    /** One generic answer for wrong password, unknown email and disabled accounts. */
    @ExceptionHandler({BadCredentialsException.class, AuthenticationException.class})
    ResponseEntity<ApiError> badCredentials(AuthenticationException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiError.of("INVALID_CREDENTIALS", "Invalid email or password"));
    }

    @ExceptionHandler({NoResourceFoundException.class, HttpRequestMethodNotSupportedException.class,
            MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class,
            ErrorResponseException.class})
    ResponseEntity<ApiError> framework(Exception e) {
        HttpStatus status = e instanceof NoResourceFoundException ? HttpStatus.NOT_FOUND
                : e instanceof HttpRequestMethodNotSupportedException ? HttpStatus.METHOD_NOT_ALLOWED
                : e instanceof ErrorResponseException er ? HttpStatus.valueOf(er.getStatusCode().value())
                : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(ApiError.of(status.name(), status.getReasonPhrase()));
    }

    /** Anything else (including a failed decision write) is a technical error; no decision is shown. */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception e) {
        log.error("unhandled error: {}", e.toString());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of("TECHNICAL_ERROR", "Something went wrong on our side. Nothing was saved."));
    }

    @ExceptionHandler(AnalysisUnavailableException.class)
    ResponseEntity<ApiError> analysisDown(AnalysisUnavailableException e) {
        log.warn("analysis unavailable: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiError.of("ANALYSIS_UNAVAILABLE", "The analysis service is unavailable. Please try again."));
    }
}
