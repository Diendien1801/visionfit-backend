package com.visionfit.identity.config;

import jakarta.servlet.ServletRequestEvent;
import jakarta.servlet.ServletRequestListener;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Tags every log line of a request with the gateway's X-Request-Id (printed by logging.pattern.correlation),
 * so grepping one id finds the request in every service. The other services also log the caller's uid;
 * here almost every request is a login or a sign-up, made before there is any user to name.
 */
final class LogContext {

    private LogContext() {
    }

    /**
     * Sets the id before the first filter and clears it only once Tomcat is done with the request, so the
     * stack trace Tomcat logs for an exception nobody handled carries the id too.
     */
    @Component
    static class RequestId implements ServletRequestListener {

        @Override
        public void requestInitialized(ServletRequestEvent event) {
            String id = ((HttpServletRequest) event.getServletRequest()).getHeader("X-Request-Id");
            if (id != null) MDC.put("requestId", id);
        }

        @Override
        public void requestDestroyed(ServletRequestEvent event) {
            // Tomcat reuses this thread for the next request: leftover ids would tag someone else's logs.
            MDC.clear();
        }
    }
}
