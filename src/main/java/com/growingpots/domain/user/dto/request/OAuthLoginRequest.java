package com.growingpots.domain.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record OAuthLoginRequest(
        @Schema(description = "소셜 로그인 제공자", example = "KAKAO")
        @NotBlank String provider,

        @Schema(description = "소셜 SDK로 발급받은 access token")
        @NotBlank String oauthAccessToken
) {
}
