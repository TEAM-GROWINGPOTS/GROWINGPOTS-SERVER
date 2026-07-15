package com.growingpots.domain.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record OAuthLoginResponse(
        @Schema(description = "온보딩 완료 여부. 기본정보입력만으로는 완료로 안 치고, PDF 분석까지 끝나야 "
                + "완료로 본다(#221) - 기본정보만 입력하고 이탈했다가 재로그인하면 false로, 온보딩 화면부터 "
                + "다시 보여줘야 함") boolean onboardingCompleted,
        @Schema(description = "닉네임") String nickname
) {
}
