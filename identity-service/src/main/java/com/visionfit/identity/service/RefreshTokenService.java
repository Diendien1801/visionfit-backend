package com.visionfit.identity.service;

import com.visionfit.identity.config.JwtProperties;
import com.visionfit.identity.domain.RefreshToken;
import com.visionfit.identity.domain.User;
import com.visionfit.identity.exception.ApiException;
import com.visionfit.identity.repository.RefreshTokenRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class RefreshTokenService {

    private static final int TOKEN_BYTE_LENGTH = 32;

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, JwtProperties jwtProperties) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtProperties = jwtProperties;
    }

    @Transactional
    public IssuedRefreshToken issue(User user) {
        String plainToken = generatePlainToken();
        RefreshToken entity = new RefreshToken();
        entity.setUser(user);
        entity.setTokenHash(hashToken(plainToken));
        entity.setExpiresAt(Instant.now().plusMillis(jwtProperties.getRefreshExpirationMs()));
        refreshTokenRepository.save(entity);
        return new IssuedRefreshToken(plainToken, jwtProperties.getRefreshExpirationMs());
    }

    @Transactional
    public RotationResult rotate(String plainRefreshToken) {
        String tokenHash = hashToken(plainRefreshToken);
        RefreshToken stored = refreshTokenRepository.findByTokenHashAndRevokedAtIsNull(tokenHash)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token"));

        if (!stored.isActive()) {
            stored.setRevokedAt(Instant.now());
            refreshTokenRepository.save(stored);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token");
        }

        stored.setRevokedAt(Instant.now());
        refreshTokenRepository.save(stored);

        User user = stored.getUser();
        IssuedRefreshToken newRefresh = issue(user);
        return new RotationResult(user, newRefresh);
    }

    @Transactional
    public void revoke(String plainRefreshToken) {
        String tokenHash = hashToken(plainRefreshToken);
        RefreshToken stored = refreshTokenRepository.findByTokenHashAndRevokedAtIsNull(tokenHash)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token"));
        stored.setRevokedAt(Instant.now());
        refreshTokenRepository.save(stored);
    }

    public String hashToken(String plainToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(plainToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private String generatePlainToken() {
        byte[] bytes = new byte[TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record IssuedRefreshToken(String plainToken, long expiresInMs) {
    }

    public record RotationResult(User user, IssuedRefreshToken refreshToken) {
    }
}
