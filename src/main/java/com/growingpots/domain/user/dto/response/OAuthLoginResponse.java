package com.growingpots.domain.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record OAuthLoginResponse(
        @Schema(description = "온보딩 완료 여부. 기본정보입력·PDF 분석만으로는 완료로 안 치고, 분석 확인 화면에서 "
                + "\"확인\"까지 눌러야 완료로 본다 - 그 전에 이탈했다가 재로그인하면 false로, 이탈한 "
                + "단계(온보딩 화면 또는 분석 확인 화면)부터 다시 보여줘야 함") boolean onboardingCompleted,
        @Schema(description = "닉네임") String nickname
) {
}
