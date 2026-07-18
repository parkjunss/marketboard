package org.juns.marketboardbackend.auth.dto;

public record TokenResponse(String accessToken, String refreshToken) {
}
