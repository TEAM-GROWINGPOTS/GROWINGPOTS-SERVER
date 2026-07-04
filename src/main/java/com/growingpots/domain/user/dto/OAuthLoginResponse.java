package com.growingpots.domain.user.dto;

public record OAuthLoginResponse(
        String accessToken,
        String refreshToken,
        boolean onboardingCompleted,
        String nickname
) {
}
