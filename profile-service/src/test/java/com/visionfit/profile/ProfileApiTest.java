package com.visionfit.profile;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real HS256 tokens, signed the way identity-service signs them, against the whole HTTP stack. */
@SpringBootTest
@AutoConfigureMockMvc
class ProfileApiTest {

    private static final String SECRET = "test-jwt-secret-at-least-32-characters-long";
    private static final String PROFILE = """
            {"sex":"FEMALE","ageYears":30,"heightCm":165,"weightKg":80,"activityLevel":"LIGHT","goal":"FAT_LOSS"}""";

    @Autowired
    private MockMvc mvc;

    @Test
    void rejectsMissingForgedExpiredAndUidlessTokens() throws Exception {
        mvc.perform(get("/api/v1/profile/me")).andExpect(status().isUnauthorized());

        String forged = token("another-secret-that-is-at-least-32-chars", Map.of("uid", UUID.randomUUID().toString()), 3600);
        mvc.perform(get("/api/v1/profile/me").header("Authorization", "Bearer " + forged))
                .andExpect(status().isUnauthorized());

        // Beyond the decoder's default 60 s clock skew.
        String expired = token(SECRET, Map.of("uid", UUID.randomUUID().toString()), -600);
        mvc.perform(get("/api/v1/profile/me").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized());

        String noUid = token(SECRET, Map.of(), 3600);
        mvc.perform(get("/api/v1/profile/me").header("Authorization", "Bearer " + noUid))
                .andExpect(status().isUnauthorized());

        String badUid = token(SECRET, Map.of("uid", "not-a-uuid"), 3600);
        mvc.perform(get("/api/v1/profile/me").header("Authorization", "Bearer " + badUid))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void notFoundUntilSavedThenReturnsTheSavedProfile() throws Exception {
        String bearer = bearerFor(UUID.randomUUID());

        mvc.perform(get("/api/v1/profile/me").header("Authorization", bearer)).andExpect(status().isNotFound());

        mvc.perform(put("/api/v1/profile/me").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(PROFILE))
                .andExpect(status().isOk())
                .andExpect(content().json(PROFILE));

        String updated = PROFILE.replace("\"weightKg\":80", "\"weightKg\":75");
        mvc.perform(put("/api/v1/profile/me").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(updated))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/profile/me").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(content().json(updated));
    }

    @Test
    void usersOnlySeeTheirOwnProfile() throws Exception {
        mvc.perform(put("/api/v1/profile/me").header("Authorization", bearerFor(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON).content(PROFILE))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/profile/me").header("Authorization", bearerFor(UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsValuesOutsideTheAppsRanges() throws Exception {
        String bearer = bearerFor(UUID.randomUUID());
        for (String invalid : new String[]{
                PROFILE.replace("\"ageYears\":30", "\"ageYears\":10"),
                PROFILE.replace("\"heightCm\":165", "\"heightCm\":300"),
                PROFILE.replace("\"goal\":\"FAT_LOSS\"", "\"goal\":\"BULK\""),
                PROFILE.replace("\"sex\":\"FEMALE\",", ""),
        }) {
            mvc.perform(put("/api/v1/profile/me").header("Authorization", bearer)
                            .contentType(MediaType.APPLICATION_JSON).content(invalid))
                    .andExpect(status().isBadRequest());
        }
    }

    private static String bearerFor(UUID userId) {
        return "Bearer " + token(SECRET, Map.of("uid", userId.toString()), 3600);
    }

    private static String token(String secret, Map<String, Object> claims, long expiresInSeconds) {
        var key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        Instant expiresAt = Instant.now().plusSeconds(expiresInSeconds);
        JwtClaimsSet.Builder body = JwtClaimsSet.builder()
                .subject("someone@visionfit.vn")
                .issuedAt(expiresAt.minusSeconds(3600))
                .expiresAt(expiresAt);
        claims.forEach(body::claim);
        var header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, body.build())).getTokenValue();
    }
}
