package com.growingpots.domain.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.growingpots.domain.user.entity.Member;
import com.growingpots.domain.user.entity.enums.OauthProvider;
import com.growingpots.domain.user.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class TokenReissueTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MemberRepository memberRepository;

    @MockitoBean
    private KakaoOAuthClient kakaoOAuthClient;

    @Test
    void 저장된_refreshToken으로_재발급하면_새_토큰_쌍을_받는다() throws Exception {
        String refreshToken = loginAndGetRefreshToken(3001L, "재발급유저");

        mockMvc.perform(post("/api/v1/auth/reissue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TokenReissueRequest(refreshToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("AUTH_200_2"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").value(org.hamcrest.Matchers.not(refreshToken)));

        Member member = memberRepository.findByOauthProviderAndOauthId(
                        OauthProvider.KAKAO, "3001")
                .orElseThrow();
        assertThat(member.getRefreshToken()).isNotEqualTo(refreshToken);
    }

    @Test
    void 저장된_토큰과_다르면_401_AUTH_005를_반환한다() throws Exception {
        String refreshToken = loginAndGetRefreshToken(3002L, "탈취의심유저");

        Member member = memberRepository.findByOauthProviderAndOauthId(
                        OauthProvider.KAKAO, "3002")
                .orElseThrow();
        member.updateRefreshToken("already-rotated-elsewhere");
        memberRepository.save(member);

        mockMvc.perform(post("/api/v1/auth/reissue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TokenReissueRequest(refreshToken))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_005"));
    }

    @Test
    void 형식이_잘못된_토큰이면_401_AUTH_001을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/auth/reissue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TokenReissueRequest("not-a-jwt"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_001"));
    }

    @Test
    void refreshToken이_비어있으면_400_CMN_002를_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/auth/reissue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TokenReissueRequest(""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN_002"));
    }

    private String loginAndGetRefreshToken(Long kakaoId, String nickname) throws Exception {
        when(kakaoOAuthClient.getUserInfo(anyString())).thenReturn(
                new KakaoUserInfoResponse(kakaoId, new KakaoUserInfoResponse.Properties(nickname), null));

        String response = mockMvc.perform(post("/api/v1/auth/oauth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new OAuthLoginRequest("KAKAO", "kakao_token_" + kakaoId))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(response).path("data").path("refreshToken").asText();
    }
}
