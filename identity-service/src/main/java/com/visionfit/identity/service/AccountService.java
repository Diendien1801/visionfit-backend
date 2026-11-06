package com.visionfit.identity.service;

import com.visionfit.identity.domain.OutboxEvent;
import com.visionfit.identity.domain.User;
import com.visionfit.identity.repository.OutboxEventRepository;
import com.visionfit.identity.repository.RefreshTokenRepository;
import com.visionfit.identity.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final IdentityUserDetailsService userDetailsService;

    public AccountService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            OutboxEventRepository outboxEventRepository,
            IdentityUserDetailsService userDetailsService
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.userDetailsService = userDetailsService;
    }

    /**
     * Deletes the account and, in the same transaction, records a user.deleted event: either both are
     * saved or neither is. The other services delete their own data when the event reaches them.
     */
    @Transactional
    public void deleteAccount(String email) {
        User user = userDetailsService.loadUserEntityByEmail(email);
        refreshTokenRepository.deleteByUser(user);
        userRepository.delete(user);
        OutboxEvent event = outboxEventRepository.save(OutboxEvent.userDeleted(user.getId(), MDC.get("requestId")));
        log.info("Account {} deleted, event {} queued", user.getId(), event.getId());
    }
}
