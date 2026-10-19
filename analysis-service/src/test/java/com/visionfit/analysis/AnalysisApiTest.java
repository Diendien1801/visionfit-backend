package com.visionfit.analysis;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.visionfit.analysis.ai.DishRecognizer;
import com.visionfit.analysis.ai.DishRecognizer.RecognizedDish;
import com.visionfit.analysis.domain.AnalysisJob;
import com.visionfit.analysis.domain.AnalysisPhoto;
import com.visionfit.analysis.repository.AnalysisJobRepository;
import com.visionfit.analysis.repository.AnalysisPhotoRepository;
import com.visionfit.analysis.service.AnalysisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The whole HTTP stack with real tokens and the real migration. The model is faked and analyses
 * run on the calling thread, so the job is finished when the upload request returns.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AnalysisApiTest {

    private static final String SECRET = "test-jwt-secret-at-least-32-characters-long";
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x01, 0x02};
    private static final RecognizedDish RICE = new RecognizedDish("Cơm trắng", 180, 130, 2.7, 28, 0.3, 95, 0.3, 0.6, 0.2);
    private static final RecognizedDish PORK = new RecognizedDish("Thịt kho trứng", 150, 230, 14, 4, 17, 88, 0.7, 0.4, 0.18);

    /** What the fake model does on the next call. */
    static Supplier<List<RecognizedDish>> nextAnswer = () -> List.of(RICE, PORK);

    @TestConfiguration
    static class FakeModel {
        @Bean
        @Primary
        DishRecognizer fakeRecognizer() {
            return (photo, mediaType) -> nextAnswer.get();
        }

        @Bean
        @Primary
        TaskExecutor syncExecutor() {
            return new SyncTaskExecutor();
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AnalysisJobRepository jobs;

    @Autowired
    private AnalysisPhotoRepository photos;

    @Autowired
    private AnalysisService service;

    @BeforeEach
    void modelFindsTwoDishes() {
        nextAnswer = () -> List.of(RICE, PORK);
    }

    @Test
    void everyEndpointNeedsAToken() throws Exception {
        mvc.perform(upload(JPEG, "image/jpeg")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/analysis/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    @Test
    void uploadAnswersAtOnceAndTheJobCompletesInTheBackground() throws Exception {
        String bearer = bearerFor(UUID.randomUUID());

        String id = submit(bearer)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.stage").value("RECOGNIZING"))
                .andExpect(jsonPath("$.mealType").value("DINNER"))
                .andExpect(jsonPath("$.capturedAt").value("2026-10-19T19:05:00"))
                .andExpect(jsonPath("$.photoRef").value("bundled://family_tray"))
                .andExpect(jsonPath("$.photoSizeBytes").value(JPEG.length))
                .andReturn().getResponse().getContentAsString().replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

        mvc.perform(get("/api/v1/analysis/" + id).header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("COMPLETED"))
                .andExpect(jsonPath("$.failure").value(nullValue()))
                .andExpect(jsonPath("$.timings.queueMs").value(notNullValue()))
                .andExpect(jsonPath("$.timings.recognitionMs").value(notNullValue()))
                .andExpect(jsonPath("$.timings.calculationMs").value(notNullValue()))
                .andExpect(jsonPath("$.detections", hasSize(2)))
                .andExpect(jsonPath("$.detections[0].id").value(notNullValue()))
                .andExpect(jsonPath("$.detections[0].name").value("Cơm trắng"))
                .andExpect(jsonPath("$.detections[0].grams").value(180))
                .andExpect(jsonPath("$.detections[0].per100g.kcal").value(130.0))
                .andExpect(jsonPath("$.detections[0].confidence").value(95))
                .andExpect(jsonPath("$.detections[0].region.centerX").value(0.3))
                .andExpect(jsonPath("$.detections[1].name").value("Thịt kho trứng"));
    }

    @Test
    void noFoodFailsAndARetryAnalyzesTheStoredPhotoAgain() throws Exception {
        String bearer = bearerFor(UUID.randomUUID());
        nextAnswer = List::of;

        String id = idOf(submit(bearer));
        mvc.perform(get("/api/v1/analysis/" + id).header("Authorization", bearer))
                .andExpect(jsonPath("$.stage").value("FAILED"))
                .andExpect(jsonPath("$.failure").value("NO_FOOD_DETECTED"));

        nextAnswer = () -> List.of(RICE);
        mvc.perform(post("/api/v1/analysis/" + id + "/retry").header("Authorization", bearer))
                .andExpect(status().isAccepted());
        mvc.perform(get("/api/v1/analysis/" + id).header("Authorization", bearer))
                .andExpect(jsonPath("$.stage").value("COMPLETED"))
                .andExpect(jsonPath("$.failure").value(nullValue()))
                .andExpect(jsonPath("$.detections", hasSize(1)));

        mvc.perform(post("/api/v1/analysis/" + id + "/retry").header("Authorization", bearer))
                .andExpect(status().isConflict());
    }

    @Test
    void aModelErrorFailsTheJobInsteadOfLosingIt() throws Exception {
        String bearer = bearerFor(UUID.randomUUID());
        nextAnswer = () -> {
            throw new IllegalStateException("overloaded");
        };

        String id = idOf(submit(bearer));

        mvc.perform(get("/api/v1/analysis/" + id).header("Authorization", bearer))
                .andExpect(jsonPath("$.stage").value("FAILED"))
                .andExpect(jsonPath("$.failure").value("INVALID_AI_RESPONSE"))
                .andExpect(jsonPath("$.timings.recognitionMs").value(notNullValue()));
    }

    @Test
    void usersOnlySeeTheirOwnJobs() throws Exception {
        String id = idOf(submit(bearerFor(UUID.randomUUID())));
        String stranger = bearerFor(UUID.randomUUID());

        mvc.perform(get("/api/v1/analysis/" + id).header("Authorization", stranger)).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/analysis/" + id + "/retry").header("Authorization", stranger)).andExpect(status().isNotFound());
    }

    @Test
    void rejectsUploadsTheModelCannotRead() throws Exception {
        String bearer = bearerFor(UUID.randomUUID());

        mvc.perform(upload("hello".getBytes(), "text/plain").header("Authorization", bearer)).andExpect(status().isBadRequest());
        mvc.perform(upload(new byte[0], "image/jpeg").header("Authorization", bearer)).andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/v1/analysis").file(new MockMultipartFile("photo", "meal.jpg", "image/jpeg", JPEG))
                        .param("capturedAt", "2026-10-19T19:05:00").param("mealType", "BRUNCH").param("photoRef", "bundled://x")
                        .header("Authorization", bearer))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/v1/analysis").file(new MockMultipartFile("photo", "meal.jpg", "image/jpeg", JPEG))
                        .param("mealType", "DINNER").param("photoRef", "bundled://x").header("Authorization", bearer))
                .andExpect(status().isBadRequest());
    }

    @Test
    void jobsCutOffByARestartRunAgainOnStartup() throws Exception {
        UUID owner = UUID.randomUUID();
        AnalysisJob stuck = new AnalysisJob(owner, AnalysisJob.MealType.LUNCH, LocalDateTime.of(2026, 10, 19, 12, 0),
                "bundled://com_tam", JPEG.length, Instant.now());
        stuck.start(Instant.now()); // a worker had taken it when the service stopped
        UUID id = jobs.saveAndFlush(stuck).getId();
        photos.save(new AnalysisPhoto(id, "image/jpeg", JPEG));

        service.resumeUnfinished();

        mvc.perform(get("/api/v1/analysis/" + id).header("Authorization", bearerFor(owner)))
                .andExpect(jsonPath("$.stage").value("COMPLETED"));
    }

    private org.springframework.test.web.servlet.ResultActions submit(String bearer) throws Exception {
        return mvc.perform(upload(JPEG, "image/jpeg").header("Authorization", bearer));
    }

    private String idOf(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString().replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
    }

    private static MockMultipartHttpServletRequestBuilder upload(byte[] photo, String contentType) {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/v1/analysis")
                .file(new MockMultipartFile("photo", "meal.jpg", contentType, photo));
        request.param("capturedAt", "2026-10-19T19:05:00");
        request.param("mealType", "DINNER");
        request.param("photoRef", "bundled://family_tray");
        return request;
    }

    private static String bearerFor(UUID userId) {
        var key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject("someone@visionfit.vn")
                .claim("uid", userId.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .build();
        return "Bearer " + encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
