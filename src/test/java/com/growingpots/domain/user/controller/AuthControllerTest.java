package com.growingpots.domain.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.growingpots.domain.transcript.entity.GraduationAnalysisSummary;
import com.growingpots.domain.transcript.repository.GraduationAnalysisSummaryRepository;
import com.growingpots.domain.university.entity.Department;
import com.growingpots.domain.university.entity.School;
import com.growingpots.domain.university.repository.DepartmentRepository;
import com.growingpots.domain.university.repository.SchoolRepository;
import com.growingpots.domain.user.client.KakaoOAuthClient;
import com.growingpots.domain.user.client.KakaoUserInfoResponse;
import com.growingpots.domain.user.entity.Member;
import com.growingpots.domain.user.entity.StudentMajor;
import com.growingpots.domain.user.entity.StudentProfile;
import com.growingpots.domain.user.entity.enums.OauthProvider;
import com.growingpots.domain.user.repository.MemberRepository;
import com.growingpots.domain.user.repository.StudentMajorRepository;
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

    @Autowired
    private StudentMajorRepository studentMajorRepository;

    @Autowired
    private GraduationAnalysisSummaryRepository graduationAnalysisSummaryRepository;

    @MockitoBean
    private KakaoOAuthClient kakaoOAuthClient;

    @AfterEach
    void tearDown() {
        graduationAnalysisSummaryRepository.deleteAll();
        studentMajorRepository.deleteAll();
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
                .andExpect(cookie().exists("accessToken"))
                .andExpect(cookie().httpOnly("accessToken", true))
                .andExpect(cookie().exists("refreshToken"))
                .andExpect(cookie().httpOnly("refreshToken", true));
    }

    // 기본정보입력만 하고 PDF를 아직 안 올린 상태(GraduationAnalysisSummary 없음)는 온보딩 완료가
    // 아니다(#221) - 재로그인 시 온보딩 화면부터 다시 보여줘야 하기 때문에, StudentProfile 존재만으로
    // true를 주면 PDF 업로드 화면을 건너뛰고 잘못된 곳으로 보내게 된다.
    @Test
    void 기본정보만_입력하고_PDF를_안_올렸으면_onboardingCompleted가_false다() throws Exception {
        School school = schoolRepository.save(School.builder().name("경희대학교 국제캠퍼스-2002").build());
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
                .andExpect(jsonPath("$.data.onboardingCompleted").value(false));
    }

    // PDF 분석까지만 끝나고 분석확인 화면에서 "확인"을 아직 안 눌렀으면 아직 온보딩 완료가 아니다
    // (기존 기준은 PDF 분석 완료 시점이었으나 분석확인 화면에서 이탈하는 케이스를 못 잡아서 변경).
    @Test
    void PDF_분석까지만_끝나고_확인을_안_눌렀으면_onboardingCompleted가_false다() throws Exception {
        School school = schoolRepository.save(School.builder().name("경희대학교 국제캠퍼스-2003").build());
        Department department = departmentRepository.save(Department.builder()
                .school(school)
                .college("공과대학")
                .name("컴퓨터공학과")
                .build());

        Member member = memberRepository.save(Member.builder()
                .nickname("PDF분석완료회원")
                .oauthProvider(OauthProvider.KAKAO)
                .oauthId("2003")
                .email(null)
                .build());
        StudentProfile profile = studentProfileRepository.save(StudentProfile.builder()
                .member(member)
                .school(school)
                .department(department)
                .admissionYear(2022)
                .build());
        StudentMajor mainMajor = studentMajorRepository.save(StudentMajor.builder()
                .studentProfile(profile)
                .department(department)
                .majorType(StudentMajor.MajorType.MAIN)
                .track(null)
                .build());
        graduationAnalysisSummaryRepository.save(GraduationAnalysisSummary.builder()
                .studentMajor(mainMajor)
                .build());

        when(kakaoOAuthClient.getUserInfo(anyString())).thenReturn(
                new KakaoUserInfoResponse(2003L,
                        new KakaoUserInfoResponse.Properties("PDF분석완료회원"),
                        null));

        mockMvc.perform(post("/api/v1/auth/oauth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequestFixture("KAKAO", "kakao_access_token_xxx"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.onboardingCompleted").value(false));
    }

    // 분석확인 화면에서 "확인"까지 누른(onboardingConfirmedAt 있음) 회원만 진짜 온보딩 완료다.
    @Test
    void 분석확인까지_완료한_회원은_onboardingCompleted가_true다() throws Exception {
        School school = schoolRepository.save(School.builder().name("경희대학교 국제캠퍼스-2004").build());
        Department department = departmentRepository.save(Department.builder()
                .school(school)
                .college("공과대학")
                .name("컴퓨터공학과")
                .build());

        Member member = memberRepository.save(Member.builder()
                .nickname("온보딩완료회원")
                .oauthProvider(OauthProvider.KAKAO)
                .oauthId("2004")
                .email(null)
                .build());
        StudentProfile profile = studentProfileRepository.save(StudentProfile.builder()
                .member(member)
                .school(school)
                .department(department)
                .admissionYear(2022)
                .build());
        StudentMajor mainMajor = studentMajorRepository.save(StudentMajor.builder()
                .studentProfile(profile)
                .department(department)
                .majorType(StudentMajor.MajorType.MAIN)
                .track(null)
                .build());
        graduationAnalysisSummaryRepository.save(GraduationAnalysisSummary.builder()
                .studentMajor(mainMajor)
                .build());
        profile.confirmOnboarding();
        studentProfileRepository.save(profile);

        when(kakaoOAuthClient.getUserInfo(anyString())).thenReturn(
                new KakaoUserInfoResponse(2004L,
                        new KakaoUserInfoResponse.Properties("온보딩완료회원"),
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
                        .cookie(new Cookie("accessToken", refreshToken)))
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