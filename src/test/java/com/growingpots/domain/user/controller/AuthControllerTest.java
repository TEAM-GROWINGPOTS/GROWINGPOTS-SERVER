package com.growingpots.domain.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.growingpots.domain.university.entity.Department;
import com.growingpots.domain.university.entity.School;
import com.growingpots.domain.university.repository.DepartmentRepository;
import com.growingpots.domain.university.repository.SchoolRepository;
import com.growingpots.domain.user.client.KakaoOAuthClient;
import com.growingpots.domain.user.client.KakaoUserInfoResponse;
import com.growingpots.domain.user.entity.Member;
import com.growingpots.domain.user.entity.StudentProfile;
import com.growingpots.domain.user.entity.enums.OauthProvider;
import com.growingpots.domain.user.repository.MemberRepository;
import com.growingpots.domain.user.repository.StudentProfileRepository;
import com.growingpots.global.exception.BaseException;
import com.growingpots.global.response.error.ErrorCode;
import com.growingpots.global.security.JwtTokenProvider;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private StudentProfileRepository studentProfileRepository;

    @Autowired
    private SchoolRepository schoolRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @MockitoBean
    private KakaoOAuthClient kakaoOAuthClient;

    @AfterEach
    void tearDown() {
        studentProfileRepository.deleteAll();
        memberRepository.deleteAll();
        departmentRepository.deleteAll();
        schoolRepository.deleteAll();
    }

    @Test
    void 신규_카카오_사용자는_온보딩_미완료_상태로_로그인에_성공한다() throws Exception {
        when(kakaoOAuthClient.getUserInfo(anyString())).thenReturn(
                new KakaoUserInfoResponse(1001L,
                        new KakaoUserInfoResponse.Properties("김서영"),
                        new KakaoUserInfoResponse.KakaoAccount("test@example.com")));

        mockMvc.perform(post("/api/v1/auth/oauth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequestFixture("KAKAO", "kakao_access_token_xxx"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("AUTH_200"))
                .andExpect(jsonPath("$.data.nickname").value("김서영"))
                .andExpect(jsonPath("$.data.onboardingCompleted").value(false))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(cookie().exists("refreshToken"))
                .andExpect(cookie().httpOnly("refreshToken", true));
    }

    @Test
    void 온보딩을_완료한_기존_회원은_onboardingCompleted가_true다() throws Exception {
        School school = schoolRepository.save(School.builder().name("경희대학교 국제캠퍼스").build());
        Department department = departmentRepository.save(Department.builder()
                .school(school)
                .college("공과대학")
                .name("컴퓨터공학과")
                .build());

        Member member = memberRepository.save(Member.builder()
                .nickname("기존회원")
                .oauthProvider(OauthProvider.KAKAO)
                .oauthId("2002")
                .email(null)
                .build());
        studentProfileRepository.save(StudentProfile.builder()
                .member(member)
                .school(school)
                .department(department)
                .admissionYear(2022)
                .build());

        when(kakaoOAuthClient.getUserInfo(anyString())).thenReturn(
                new KakaoUserInfoResponse(2002L,
                        new KakaoUserInfoResponse.Properties("기존회원"),
                        null));

        mockMvc.perform(post("/api/v1/auth/oauth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequestFixture("KAKAO", "kakao_access_token_xxx"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.onboardingCompleted").value(true));
    }

    @Test
    void 지원하지_않는_소셜_로그인이면_400_AUTH_003을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/auth/oauth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequestFixture("GOOGLE", "some_token"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH_003"));
    }

    @Test
    void 카카오_토큰이_유효하지_않으면_401_AUTH_001을_반환한다() throws Exception {
        when(kakaoOAuthClient.getUserInfo(anyString()))
                .thenThrow(new BaseException(ErrorCode.INVALID_TOKEN));

        mockMvc.perform(post("/api/v1/auth/oauth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequestFixture("KAKAO", "invalid_token"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_001"));
    }

    @Test
    void 카카오_서버_통신에_실패하면_502_AUTH_004를_반환한다() throws Exception {
        when(kakaoOAuthClient.getUserInfo(anyString()))
                .thenThrow(new BaseException(ErrorCode.OAUTH_SERVER_ERROR));

        mockMvc.perform(post("/api/v1/auth/oauth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequestFixture("KAKAO", "any_token"))))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("AUTH_004"));
    }

    @Test
    void oauthAccessToken이_비어있으면_400_CMN_002를_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/auth/oauth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequestFixture("KAKAO", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN_002"));
    }

    @Test
    void 리프레시_토큰으로_일반_API_호출_시_401_AUTH_001을_반환한다() throws Exception {
        String refreshToken = jwtTokenProvider.generateRefreshToken("1");

        mockMvc.perform(get("/api/v1/students/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + refreshToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_001"));
    }

    @Test
    void 액세스_토큰으로_재발급_요청_시_401_AUTH_001을_반환한다() throws Exception {
        String accessToken = jwtTokenProvider.generateToken("1");

        mockMvc.perform(post("/api/v1/auth/reissue")
                        .cookie(new Cookie("refreshToken", accessToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_001"));
    }

    private record LoginRequestFixture(String provider, String oauthAccessToken) {
    }
}