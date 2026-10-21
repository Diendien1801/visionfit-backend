package com.visionfit.gateway;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;

/**
 * When a service cannot answer, the gateway says so instead of a bare 500, which would read as
 * a bug in the gateway: 502 when the service is down or drops the connection, 504 when it is too slow.
 */
@RestControllerAdvice
class GatewayErrors {

    private static final Logger log = LoggerFactory.getLogger(GatewayErrors.class);

    @ExceptionHandler(ResourceAccessException.class)
    ProblemDetail serviceUnreachable(ResourceAccessException e, HttpServletRequest request) {
        Throwable cause = e.getMostSpecificCause();
        boolean timedOut = cause instanceof HttpTimeoutException || cause instanceof SocketTimeoutException;
        HttpStatus status = timedOut ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.BAD_GATEWAY;
        log.warn("{} {} -> {}: {}", request.getMethod(), request.getRequestURI(), status.value(), e.getMessage());
        return ProblemDetail.forStatusAndDetail(status,
                timedOut ? "The service did not answer in time." : "The service is unavailable.");
    }
}
