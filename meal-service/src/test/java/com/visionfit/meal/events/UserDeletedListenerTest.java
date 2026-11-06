package com.visionfit.meal.events;

import com.jayway.jsonpath.JsonPath;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The listener is called directly, with the body exactly as identity-service's outbox writes it. */
@SpringBootTest
@AutoConfigureMockMvc
class UserDeletedListenerTest {

    private static final String SECRET = "test-jwt-secret-at-least-32-characters-long";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserDeletedListener listener;

    @Autowired
    private MessageConverter messageConverter;

    @Test
    void deletesOnlyTheDeletedUsersMealsAndCopesWithTheSameEventTwice() throws Exception {
        UUID an = UUID.randomUUID();
        UUID binh = UUID.randomUUID();
        saveMeal(an);
        saveMeal(an);
        saveMeal(binh);

        UserDeletedListener.UserDeleted event = read("{\"userId\":\"" + an + "\"}");
        listener.onUserDeleted(event, "event-1", "req-1");
        // Delivered again, as after a crash before the ack: nothing left to delete, no error.
        listener.onUserDeleted(event, "event-1", "req-1");

        assertThat(mealCount(an)).isZero();
        assertThat(mealCount(binh)).isEqualTo(1);
    }

    /** Reads the body the way the listener container does, through the JSON converter. */
    private UserDeletedListener.UserDeleted read(String body) {
        Message message = MessageBuilder.withBody(body.getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .build();
        message.getMessageProperties().setInferredArgumentType(UserDeletedListener.UserDeleted.class);
        return (UserDeletedListener.UserDeleted) messageConverter.fromMessage(message);
    }

    private void saveMeal(UUID userId) throws Exception {
        mvc.perform(post("/api/v1/meals").header("Authorization", bearerFor(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id":"%s","jobId":null,"type":"LUNCH","loggedAt":"2026-11-04T12:00:00","photoUrl":null,"isEdited":false,
                                 "items":[{"name":"Phở","grams":400,"per100g":{"kcal":60,"proteinG":4,"carbsG":8,"fatG":1.5},"confidence":null}]}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated());
    }

    /** The diary has one entry per day asked for; one day here. */
    private int mealCount(UUID userId) throws Exception {
        String body = mvc.perform(get("/api/v1/meals?from=2026-11-04&to=2026-11-04").header("Authorization", bearerFor(userId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.read(body, "$[0].mealCount");
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
