package com.growingpots.domain.user.client;

import com.fasterxml.jackson.annotation.JsonProperty;

// 카카오가 내려주는 응답 양식
public record KakaoUserInfoResponse(
        Long id,
        Properties properties,
        @JsonProperty("kakao_account") KakaoAccount kakaoAccount
) {
    public record Properties(String nickname) {
    }

    public record KakaoAccount(String email) {
    }
}
