package com.visionfit.gateway;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.server.mvc.common.MvcUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Gives every request an id, sent to the service and back to the app in `X-Request-Id`, and logs
 * one line per request: what came in, where it went, how it ended. Runs first, so requests the
 * rate limiter rejects are logged too.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestLogFilter extends OncePerRequestFilter {

    static final String REQUEST_ID = "X-Request-Id";

    private static final Logger log = LoggerFactory.getLogger(RequestLogFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // Always a new id: one sent by the caller could be faked or break the log line.
        String id = UUID.randomUUID().toString();
        response.setHeader(REQUEST_ID, id);
        long start = System.nanoTime();
        try {
            chain.doFilter(new WithRequestId(request, id), response);
        } finally {
            // Route and target stay empty when nothing was forwarded: no matching route, or rate limited.
            log.info("{} {} -> {} {} {} in {} ms [id={}, client={}]",
                    request.getMethod(), request.getRequestURI(),
                    Objects.requireNonNullElse(request.getAttribute(MvcUtils.GATEWAY_ROUTE_ID_ATTR), "-"),
                    Objects.requireNonNullElse(request.getAttribute(MvcUtils.GATEWAY_REQUEST_URL_ATTR), "-"),
                    response.getStatus(), (System.nanoTime() - start) / 1_000_000, id, request.getRemoteAddr());
        }
    }

    /** The incoming request plus the `X-Request-Id` header, which the gateway copies to the service. */
    private static final class WithRequestId extends HttpServletRequestWrapper {

        private final String id;

        WithRequestId(HttpServletRequest request, String id) {
            super(request);
            this.id = id;
        }

        @Override
        public String getHeader(String name) {
            return REQUEST_ID.equalsIgnoreCase(name) ? id : super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            return REQUEST_ID.equalsIgnoreCase(name) ? Collections.enumeration(List.of(id)) : super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            List<String> names = Collections.list(super.getHeaderNames());
            names.removeIf(REQUEST_ID::equalsIgnoreCase);
            names.add(REQUEST_ID);
            return Collections.enumeration(names);
        }
    }
}
