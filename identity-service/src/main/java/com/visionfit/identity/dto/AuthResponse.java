package com.visionfit.identity.dto;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresInMs,
        String email
) {
    public static AuthResponse bearer(String accessToken, long expiresInMs, String email) {
        return new AuthResponse(accessToken, "Bearer", expiresInMs, email);
    }
}
