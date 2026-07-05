package com.growingpots.domain.user.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.growingpots.domain.user.client.KakaoOAuthClient;
import com.growingpots.domain.user.client.KakaoUserInfoResponse;
import com.growingpots.domain.user.dto.request.OAuthLoginRequest;
import com.growingpots.domain.user.dto.request.TokenReissueRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// refreshToken 만료 판정만 검증하기 위해 만료 시간을 짧게 오버라이드한 별도 컨텍스트.
// JWT의 exp/iat는 초 단위라 1초 미만으로 설정하면 "expiresAt must be after issuedAt"로 아예 발급이 실패하니 1초 이상으로 둔다.
// JwtTokenProvider의 timestamp validator는 clock skew=0이라, 만료 시각을 지나면 바로 만료로 판정된다.
@ActiveProfiles("test")
@SpringBootTest(properties = "jwt.refresh-expiration=1000")
@AutoConfigureMockMvc
class TokenReissueExpiredTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private KakaoOAuthClient kakaoOAuthClient;

    @Test
    void 만료된_refreshToken이면_401_AUTH_002를_반환한다() throws Exception {
        when(kakaoOAuthClient.getUserInfo(anyString())).thenReturn(
                new KakaoUserInfoResponse(4001L, new KakaoUserInfoResponse.Properties("만료유저"), null));

        String response = mockMvc.perform(post("/api/v1/auth/oauth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OAuthLoginRequest("KAKAO", "kakao_token_4001"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String refreshToken = objectMapper.readTree(response).path("data").path("refreshToken").asText();

        Thread.sleep(2000);

        mockMvc.perform(post("/api/v1/auth/reissue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TokenReissueRequest(refreshToken))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_002"));
    }
}
