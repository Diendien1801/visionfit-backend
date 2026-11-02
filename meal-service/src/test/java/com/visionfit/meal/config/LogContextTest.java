package com.visionfit.meal.config;

import jakarta.servlet.ServletRequestEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LogContextTest {

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    @Test
    void tagsTheRequestWithBothIdsAndClearsThemAfterwards() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", "req-1");
        ServletRequestEvent event = new ServletRequestEvent(new MockServletContext(), request);
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "HS256").claim("uid", "user-1").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        Map<String, String> seenByTheController = new HashMap<>();

        LogContext.RequestId listener = new LogContext.RequestId();
        listener.requestInitialized(event);
        new LogContext.UserId().doFilter(request, new MockHttpServletResponse(),
                (req, res) -> seenByTheController.putAll(MDC.getCopyOfContextMap()));
        listener.requestDestroyed(event);

        assertEquals(Map.of("requestId", "req-1", "userId", "user-1"), seenByTheController);
        assertNull(MDC.get("requestId"));
        assertNull(MDC.get("userId"));
    }
}
