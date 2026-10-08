package com.visionfit.identity.dto;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInMs,
        long refreshExpiresInMs,
        String email
) {
    public static AuthResponse bearer(
            String accessToken,
            String refreshToken,
            long expiresInMs,
            long refreshExpiresInMs,
            String email
    ) {
        return new AuthResponse(accessToken, refreshToken, "Bearer", expiresInMs, refreshExpiresInMs, email);
    }
}
