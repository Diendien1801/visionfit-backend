package com.visionfit.meal.events;

import com.visionfit.meal.repository.MealRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Deletes a user's meals once identity-service has deleted their account. The message is acknowledged
 * after the delete returns: a crash in between delivers it again, which is harmless because deleting
 * meals that are already gone deletes nothing. An exception is retried, then dead-lettered.
 */
@Component
class UserDeletedListener {

    private static final Logger log = LoggerFactory.getLogger(UserDeletedListener.class);

    /** The body identity-service sends; fields it adds later are ignored. */
    record UserDeleted(UUID userId) {
    }

    private final MealRepository mealRepository;

    UserDeletedListener(MealRepository mealRepository) {
        this.mealRepository = mealRepository;
    }

    @RabbitListener(queues = UserEventsConfig.QUEUE)
    void onUserDeleted(
            UserDeleted event,
            @Header(AmqpHeaders.MESSAGE_ID) String eventId,
            @Header(name = "requestId", required = false) String requestId
    ) {
        // Listener threads have no request: tag the logs with the one that deleted the account.
        if (requestId != null) MDC.put("requestId", requestId);
        MDC.put("userId", event.userId().toString());
        try {
            int deleted = mealRepository.deleteByUserId(event.userId());
            log.info("Account deleted (event {}): removed {} meals", eventId, deleted);
        } finally {
            MDC.clear();
        }
    }
}
