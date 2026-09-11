package com.rocketcredit.gateway.web;

import com.rocketcredit.gateway.api.CheckoutRequest;
import com.rocketcredit.gateway.api.CheckoutResponse;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;

@Component
public class ErrorMapper {

    public record Mapping(int status, String code) {}

    public Mapping map(Throwable t) {
        if (t instanceof IllegalArgumentException || t instanceof MethodArgumentNotValidException
                || t instanceof javax.validation.ConstraintViolationException) {
            return new Mapping(HttpStatus.UNPROCESSABLE_ENTITY.value(), "INVALID_REQUEST");
        }
        if (t instanceof com.rocketcredit.gateway.clients.CircuitOpenException
                || t instanceof com.rocketcredit.gateway.clients.BulkheadFullException
                || t instanceof ResourceAccessException
                || t instanceof java.net.ConnectException
                || t instanceof java.net.SocketTimeoutException) {
            return new Mapping(HttpStatus.SERVICE_UNAVAILABLE.value(), "SERVICE_UNAVAILABLE");
        }
        if (t instanceof com.rocketcredit.gateway.clients.UnpinnedEndpointException) {
            return new Mapping(HttpStatus.INTERNAL_SERVER_ERROR.value(), "INTERNAL_ERROR");
        }
        if (t instanceof HttpStatusCodeException hsce) {
            try {
                return hsce.getStatusCode().is5xxServerError()
                    ? new Mapping(HttpStatus.SERVICE_UNAVAILABLE.value(), "SERVICE_UNAVAILABLE")
                    : new Mapping(HttpStatus.BAD_GATEWAY.value(), "UPSTREAM_ERROR");
            } catch (Throwable ignore) {
                return new Mapping(HttpStatus.BAD_GATEWAY.value(), "UPSTREAM_ERROR");
            }
        }
        if (t instanceof IllegalStateException) {
            return new Mapping(HttpStatus.BAD_GATEWAY.value(), "UPSTREAM_ERROR");
        }
        return new Mapping(HttpStatus.INTERNAL_SERVER_ERROR.value(), "INTERNAL_ERROR");
    }

    public CheckoutResponse toCheckoutError(CheckoutRequest req, Mapping m) {
        CheckoutResponse err = new CheckoutResponse();
        err.setApproved(false);
        if (req != null) {
            err.setPartnerPaymentId(req.getPartnerPaymentId());
            err.setInstallmentDurationMonths(req.getInstallmentDurationMonths());
        }
        err.setReason(JsonNullable.of(m.code()));
        err.setSchedule(java.util.List.of());
        return err;
    }
}
