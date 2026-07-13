package com.growingpots.domain.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record OAuthLoginResponse(
        @Schema(description = "온보딩(학적 정보 입력) 완료 여부") boolean onboardingCompleted,
        @Schema(description = "닉네임") String nickname
) {
}
