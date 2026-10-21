package com.visionfit.gateway;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Requests per caller per minute. Each request counts in exactly one bucket:
 * <ul>
 *   <li>sign-in endpoints, per client IP: slows down password guessing;</li>
 *   <li>starting a photo analysis (POST), per access token: every one costs a Claude call;</li>
 *   <li>everything else, per access token, or per IP without one.</li>
 * </ul>
 * The key is the raw token, not its `uid` claim: the gateway does not verify tokens, so keying on
 * `uid` would let a forged token use up another user's quota. A forged token itself only reaches
 * a service that answers 401.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
class RateLimitFilter extends OncePerRequestFilter {

    private final int authPerMinute;
    private final int analysisPerMinute;
    private final int apiPerMinute;

    // ponytail: fixed one-minute window counted in this process; allows up to 2x the limit across a
    // window edge, and each gateway instance counts alone. Move to Redis (Bucket4j) when running more than one.
    private long minute;
    private Map<String, Integer> counts = new HashMap<>();

    RateLimitFilter(@Value("${visionfit.rate-limit.auth-per-minute}") int authPerMinute,
                    @Value("${visionfit.rate-limit.analysis-per-minute}") int analysisPerMinute,
                    @Value("${visionfit.rate-limit.api-per-minute}") int apiPerMinute) {
        this.authPerMinute = authPerMinute;
        this.analysisPerMinute = analysisPerMinute;
        this.apiPerMinute = apiPerMinute;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // Decoded path, so `/api/v1/%61uth/login` cannot dodge the sign-in bucket.
        String path = request.getServletPath();
        String ip = request.getRemoteAddr();
        String token = request.getHeader(HttpHeaders.AUTHORIZATION);
        String caller = token != null ? token : ip;

        String bucket;
        int limit;
        if (path.startsWith("/api/v1/auth/")) {
            bucket = "auth " + ip;
            limit = authPerMinute;
        } else if (path.startsWith("/api/v1/analysis") && "POST".equals(request.getMethod())) {
            bucket = "analysis " + caller;
            limit = analysisPerMinute;
        } else {
            bucket = "api " + caller;
            limit = apiPerMinute;
        }

        long now = System.currentTimeMillis();
        if (count(bucket, now / 60_000) <= limit) {
            chain.doFilter(request, response);
            return;
        }
        long retryAfter = 60 - (now / 1000) % 60;
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Too Many Requests\",\"status\":429,"
                + "\"detail\":\"Too many requests, try again in " + retryAfter + " s.\"}");
    }

    /** Counts this request and returns the bucket's total for the current minute. */
    private synchronized int count(String bucket, long minute) {
        if (minute != this.minute) {
            this.minute = minute;
            counts = new HashMap<>();
        }
        return counts.merge(bucket, 1, Integer::sum);
    }
}
