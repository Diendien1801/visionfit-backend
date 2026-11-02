package com.visionfit.meal.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequestEvent;
import jakarta.servlet.ServletRequestListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Tags every log line of a request with the gateway's X-Request-Id and the caller's uid (printed by
 * logging.pattern.correlation), so grepping one id finds the request in every service.
 * The same class lives in each service.
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

    /** Runs after Spring Security (a filter without an order comes last), once the token is verified. */
    @Component
    static class UserId extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken token) {
                MDC.put("userId", token.getToken().getClaimAsString("uid"));
            }
            chain.doFilter(request, response);
        }
    }
}
