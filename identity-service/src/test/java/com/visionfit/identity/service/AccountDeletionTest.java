package com.visionfit.identity.service;

import com.visionfit.identity.domain.OutboxEvent;
import com.visionfit.identity.dto.AuthResponse;
import com.visionfit.identity.dto.RefreshTokenRequest;
import com.visionfit.identity.dto.RegisterRequest;
import com.visionfit.identity.exception.ApiException;
import com.visionfit.identity.repository.OutboxEventRepository;
import com.visionfit.identity.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.ConnectException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
class AccountDeletionTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @AfterEach
    void cleanUp() {
        outboxEventRepository.deleteAll();
        MDC.clear();
    }

    @Test
    void deletingAnAccountQueuesAUserDeletedEventInTheSameTransaction() {
        AuthResponse registered = authService.register(new RegisterRequest("leaving@example.com", "password123"));
        UUID userId = userRepository.findByEmailIgnoreCase("leaving@example.com").orElseThrow().getId();
        MDC.put("requestId", "req-delete-1");

        accountService.deleteAccount("leaving@example.com");

        assertThat(userRepository.findByEmailIgnoreCase("leaving@example.com")).isEmpty();
        assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest(registered.refreshToken())))
                .isInstanceOf(ApiException.class);
        assertThat(outboxEventRepository.findTop50BySentAtIsNullOrderByCreatedAtAsc())
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getType()).isEqualTo(OutboxEvent.USER_DELETED);
                    assertThat(event.getPayload()).isEqualTo("{\"userId\":\"" + userId + "\"}");
                    assertThat(event.getRequestId()).isEqualTo("req-delete-1");
                });
    }

    @Test
    void anEventStaysUnsentUntilRabbitMqConfirmsIt() {
        outboxEventRepository.save(OutboxEvent.userDeleted(UUID.randomUUID(), null));
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        OutboxRelay relay = new OutboxRelay(outboxEventRepository, rabbitTemplate);

        when(rabbitTemplate.invoke(any())).thenThrow(new AmqpConnectException(new ConnectException("refused")));
        relay.sendPending();
        assertThat(outboxEventRepository.findTop50BySentAtIsNullOrderByCreatedAtAsc()).hasSize(1);

        doReturn(null).when(rabbitTemplate).invoke(any());
        relay.sendPending();
        assertThat(outboxEventRepository.findTop50BySentAtIsNullOrderByCreatedAtAsc()).isEmpty();
        assertThat(outboxEventRepository.findAll()).singleElement()
                .satisfies(event -> assertThat(event.getSentAt()).isNotNull());
    }
}
