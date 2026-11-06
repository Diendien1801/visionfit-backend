package com.visionfit.analysis.events;

import com.visionfit.analysis.domain.AnalysisJob;
import com.visionfit.analysis.domain.AnalysisPhoto;
import com.visionfit.analysis.repository.AnalysisJobRepository;
import com.visionfit.analysis.repository.AnalysisPhotoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The listener is called directly, with the body exactly as identity-service's outbox writes it. */
@SpringBootTest
class UserDeletedListenerTest {

    @Autowired
    private UserDeletedListener listener;

    @Autowired
    private MessageConverter messageConverter;

    @Autowired
    private AnalysisJobRepository jobRepository;

    @Autowired
    private AnalysisPhotoRepository photoRepository;

    @Test
    void deletesOnlyTheDeletedUsersAnalysesWithTheirPhotos() {
        UUID an = UUID.randomUUID();
        UUID binh = UUID.randomUUID();
        UUID ansJob = saveJobWithPhoto(an);
        UUID binhsJob = saveJobWithPhoto(binh);

        UserDeletedListener.UserDeleted event = read("{\"userId\":\"" + an + "\"}");
        listener.onUserDeleted(event, "event-1", "req-1");
        // Delivered again, as after a crash before the ack: nothing left to delete, no error.
        listener.onUserDeleted(event, "event-1", "req-1");

        assertThat(jobRepository.existsById(ansJob)).isFalse();
        assertThat(photoRepository.existsById(ansJob)).isFalse();
        assertThat(jobRepository.existsById(binhsJob)).isTrue();
        assertThat(photoRepository.existsById(binhsJob)).isTrue();
    }

    /** Reads the body the way the listener container does, through the JSON converter. */
    private UserDeletedListener.UserDeleted read(String body) {
        Message message = MessageBuilder.withBody(body.getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .build();
        message.getMessageProperties().setInferredArgumentType(UserDeletedListener.UserDeleted.class);
        return (UserDeletedListener.UserDeleted) messageConverter.fromMessage(message);
    }

    private UUID saveJobWithPhoto(UUID userId) {
        AnalysisJob job = jobRepository.save(new AnalysisJob(
                userId, AnalysisJob.MealType.LUNCH, LocalDateTime.of(2026, 11, 4, 12, 0), "bundled://pho.jpg", 3, Instant.now()));
        photoRepository.save(new AnalysisPhoto(job.getId(), "image/jpeg", new byte[] {1, 2, 3}));
        return job.getId();
    }
}
