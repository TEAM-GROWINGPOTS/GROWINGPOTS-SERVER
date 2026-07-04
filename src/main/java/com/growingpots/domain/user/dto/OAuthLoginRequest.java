package com.growingpots.domain.user.dto;

import jakarta.validation.constraints.NotBlank;

public record OAuthLoginRequest(
        @NotBlank String provider,
        @NotBlank String oauthAccessToken
) {
}
