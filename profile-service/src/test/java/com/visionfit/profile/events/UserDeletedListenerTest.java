package com.visionfit.profile.events;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConverter;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The listener is called directly, with the body exactly as identity-service's outbox writes it. */
@SpringBootTest
@AutoConfigureMockMvc
class UserDeletedListenerTest {

    private static final String SECRET = "test-jwt-secret-at-least-32-characters-long";
    private static final String PROFILE = """
            {"sex":"FEMALE","ageYears":30,"heightCm":165,"weightKg":80,"activityLevel":"LIGHT","goal":"FAT_LOSS"}""";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserDeletedListener listener;

    @Autowired
    private MessageConverter messageConverter;

    @Test
    void deletesOnlyTheDeletedUsersProfileAndCopesWithTheSameEventTwice() throws Exception {
        UUID an = UUID.randomUUID();
        UUID binh = UUID.randomUUID();
        saveProfile(an);
        saveProfile(binh);

        UserDeletedListener.UserDeleted event = read("{\"userId\":\"" + an + "\"}");
        listener.onUserDeleted(event, "event-1", "req-1");
        // Delivered again, as after a crash before the ack: nothing left to delete, no error.
        listener.onUserDeleted(event, "event-1", "req-1");

        mvc.perform(get("/api/v1/profile/me").header("Authorization", bearerFor(an))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/profile/me").header("Authorization", bearerFor(binh))).andExpect(status().isOk());
    }

    /** Reads the body the way the listener container does, through the JSON converter. */
    private UserDeletedListener.UserDeleted read(String body) {
        Message message = MessageBuilder.withBody(body.getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .build();
        message.getMessageProperties().setInferredArgumentType(UserDeletedListener.UserDeleted.class);
        return (UserDeletedListener.UserDeleted) messageConverter.fromMessage(message);
    }

    private void saveProfile(UUID userId) throws Exception {
        mvc.perform(put("/api/v1/profile/me").header("Authorization", bearerFor(userId))
                        .contentType(MediaType.APPLICATION_JSON).content(PROFILE))
                .andExpect(status().isOk());
    }

    private static String bearerFor(UUID userId) {
        var key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject("someone@visionfit.vn")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .claim("uid", userId.toString())
                .build();
        var header = JwsHeader.with(MacAlgorithm.HS256).build();
        return "Bearer " + new NimbusJwtEncoder(new ImmutableSecret<>(key)).encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
