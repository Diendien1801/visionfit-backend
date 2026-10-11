package com.visionfit.identity.service;

import com.visionfit.identity.dto.AuthResponse;
import com.visionfit.identity.dto.RefreshTokenRequest;
import com.visionfit.identity.dto.RegisterRequest;
import com.visionfit.identity.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class RefreshTokenFlowTest {

    @Autowired
    private AuthService authService;

    @Test
    void refreshRotatesTokenAndLogoutRevokes() {
        AuthResponse registered = authService.register(
                new RegisterRequest("refresh-user@example.com", "password123")
        );
        assertThat(registered.refreshToken()).isNotBlank();

        AuthResponse refreshed = authService.refresh(new RefreshTokenRequest(registered.refreshToken()));
        assertThat(refreshed.refreshToken()).isNotBlank().isNotEqualTo(registered.refreshToken());

        assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest(registered.refreshToken())))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        authService.logout(new RefreshTokenRequest(refreshed.refreshToken()));

        assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest(refreshed.refreshToken())))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
