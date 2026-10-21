package com.visionfit.gateway;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Starts the gateway in front of four fake services. Each fake answers with its name and what it
 * received, so every assertion shows which service a path reached and what arrived there.
 * A path ending in `/slow` makes a fake hang, one ending in `/crash` makes it drop the connection.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.http.clients.read-timeout=1s",
        "visionfit.rate-limit.auth-per-minute=3",
        "visionfit.rate-limit.analysis-per-minute=3",
})
class GatewayTest {

    private static final List<HttpServer> SERVICES = new ArrayList<>();

    @LocalServerPort
    int port;

    private final HttpClient client = HttpClient.newHttpClient();

    @DynamicPropertySource
    static void fakeServices(DynamicPropertyRegistry registry) throws IOException {
        for (String name : List.of("IDENTITY", "PROFILE", "MEAL", "ANALYSIS")) {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
            server.createContext("/", exchange -> {
                String path = exchange.getRequestURI().getPath();
                if (path.endsWith("/crash")) {
                    exchange.close();
                    return;
                }
                if (path.endsWith("/slow")) {
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                int bytes = exchange.getRequestBody().readAllBytes().length;
                byte[] reply = (name + " " + exchange.getRequestMethod() + " " + exchange.getRequestURI()
                        + " auth=" + exchange.getRequestHeaders().getFirst("Authorization")
                        + " bytes=" + bytes
                        + " id=" + exchange.getRequestHeaders().getFirst("X-Request-Id")).getBytes(UTF_8);
                exchange.sendResponseHeaders(200, reply.length);
                exchange.getResponseBody().write(reply);
                exchange.close();
            });
            server.start();
            SERVICES.add(server);
            registry.add(name + "_SERVICE_PORT", () -> server.getAddress().getPort());
        }
    }

    @AfterAll
    static void stopFakeServices() {
        SERVICES.forEach(server -> server.stop(0));
    }

    @Test
    void routesEachPathToItsService() throws Exception {
        assertReached("IDENTITY POST /api/v1/auth/login auth=null bytes=2", send("POST", "/api/v1/auth/login", "{}", null));
        assertReached("IDENTITY GET /api/v1/me auth=Bearer t bytes=0", send("GET", "/api/v1/me", null, "Bearer t"));
        assertReached("PROFILE PUT /api/v1/profile/me auth=Bearer t bytes=2", send("PUT", "/api/v1/profile/me", "{}", "Bearer t"));
        assertReached("MEAL GET /api/v1/meals?from=2026-10-01&to=2026-10-21 auth=Bearer t bytes=0",
                send("GET", "/api/v1/meals?from=2026-10-01&to=2026-10-21", null, "Bearer t"));
        assertReached("MEAL GET /api/v1/meals/streak auth=Bearer t bytes=0", send("GET", "/api/v1/meals/streak", null, "Bearer t"));
        assertReached("ANALYSIS POST /api/v1/analysis/42/retry auth=Bearer t bytes=0",
                send("POST", "/api/v1/analysis/42/retry", "", "Bearer t"));
    }

    @Test
    void passesAFiveMegabytePhotoUploadThroughUnchanged() throws Exception {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--b\r\nContent-Disposition: form-data; name=\"photo\"; filename=\"meal.jpg\"\r\n"
                + "Content-Type: image/jpeg\r\n\r\n").getBytes(UTF_8));
        body.write(new byte[5 * 1024 * 1024]);
        body.write("\r\n--b--\r\n".getBytes(UTF_8));

        HttpResponse<String> response = client.send(HttpRequest.newBuilder(uri("/api/v1/analysis"))
                .header("Authorization", "Bearer uploader")
                .header("Content-Type", "multipart/form-data; boundary=b")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build(), HttpResponse.BodyHandlers.ofString());

        assertReached("ANALYSIS POST /api/v1/analysis auth=Bearer uploader bytes=" + body.size(), response);
    }

    @Test
    void unknownPathsAreNotForwarded() throws Exception {
        assertEquals(404, send("GET", "/api/v1/unknown", null, null).statusCode());
        assertEquals(404, send("GET", "/actuator/env", null, null).statusCode());
    }

    @Test
    void sendsTheSameRequestIdToTheServiceAndBackToTheApp() throws Exception {
        HttpResponse<String> response = client.send(HttpRequest.newBuilder(uri("/api/v1/me"))
                .header("X-Request-Id", "faked-by-the-caller")
                .build(), HttpResponse.BodyHandlers.ofString());

        String id = response.headers().firstValue("X-Request-Id").orElseThrow();
        assertTrue(id.matches("[0-9a-f-]{36}"), id);
        assertTrue(response.body().endsWith(" id=" + id), response.body());
    }

    @Test
    void answers502WhenAServiceDropsTheConnection() throws Exception {
        HttpResponse<String> response = send("GET", "/api/v1/profile/crash", null, "Bearer t");

        assertEquals(502, response.statusCode());
        assertEquals("application/problem+json", response.headers().firstValue("Content-Type").orElseThrow());
    }

    @Test
    void answers504WhenAServiceIsTooSlow() throws Exception {
        long start = System.currentTimeMillis();
        HttpResponse<String> response = send("GET", "/api/v1/meals/slow", null, "Bearer t");

        assertEquals(504, response.statusCode());
        assertTrue(System.currentTimeMillis() - start < 2500, "waited for the service instead of the 1 s timeout");
    }

    @Test
    void limitsPhotoAnalysisPerAccessToken() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertEquals(200, send("POST", "/api/v1/analysis", "", "Bearer spammer").statusCode());
        }
        HttpResponse<String> limited = send("POST", "/api/v1/analysis", "", "Bearer spammer");

        assertEquals(429, limited.statusCode());
        assertTrue(limited.headers().firstValue("Retry-After").isPresent());
        // Other users, and polling the jobs already started, are not affected.
        assertEquals(200, send("POST", "/api/v1/analysis", "", "Bearer someone-else").statusCode());
        assertEquals(200, send("GET", "/api/v1/analysis/42", null, "Bearer spammer").statusCode());
    }

    @Test
    void limitsSignInPerClientIp() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertEquals(200, signInFrom("203.0.113.1", "/api/v1/auth/login").statusCode());
        }

        assertEquals(429, signInFrom("203.0.113.1", "/api/v1/%61uth/login").statusCode());
        assertEquals(200, signInFrom("203.0.113.2", "/api/v1/auth/login").statusCode());
    }

    /** Arrives like a request through cloudflared: from this machine, with the phone's IP in X-Forwarded-For. */
    private HttpResponse<String> signInFrom(String ip, String path) throws Exception {
        return client.send(HttpRequest.newBuilder(uri(path))
                .header("X-Forwarded-For", ip)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> send(String method, String path, String json, String token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri(path))
                .method(method, json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json));
        if (token != null) request.header("Authorization", token);
        if (json != null) request.header("Content-Type", "application/json");
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** The fake's reply, minus the request id that differs every time. */
    private static void assertReached(String expected, HttpResponse<String> response) {
        assertEquals(200, response.statusCode(), response.body());
        assertEquals(expected, response.body().replaceFirst(" id=\\S+$", ""));
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }
}
