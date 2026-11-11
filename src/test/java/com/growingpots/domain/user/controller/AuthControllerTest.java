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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
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
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(cookie().exists("accessToken"))
                .andExpect(cookie().httpOnly("accessToken", true))
                .andExpect(cookie().exists("refreshToken"))
                .andExpect(cookie().httpOnly("refreshToken", true));
    }

    // 신규 유저 첫 로그인 시 refreshToken이 DB에 저장돼야 한다(#283) - insert()가 REQUIRES_NEW라 반환된
    // member가 detached여서 예전엔 dirty checking으로 flush되지 않아 null로 남았고, 그 세션 재발급이 401이었다.
    @Test
    void 신규_로그인_시_refreshToken이_DB에_저장된다() throws Exception {
        when(kakaoOAuthClient.getUserInfo(anyString())).thenReturn(
                new KakaoUserInfoResponse(3001L,
                        new KakaoUserInfoResponse.Properties("신규회원"),
                        new KakaoUserInfoResponse.KakaoAccount("new@example.com")));

        mockMvc.perform(post("/api/v1/auth/oauth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequestFixture("KAKAO", "kakao_access_token_xxx"))))
                .andExpect(status().isOk());

        Member saved = memberRepository.findByOauthProviderAndOauthId(OauthProvider.KAKAO, "3001").orElseThrow();
        assertThat(saved.getRefreshToken()).isNotBlank();
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

    @Test
    void 재발급에_성공하면_새_accessToken을_응답_body로_반환한다() throws Exception {
        Member member = memberRepository.save(Member.builder()
                .nickname("재발급회원")
                .oauthProvider(OauthProvider.KAKAO)
                .oauthId("2005")
                .email(null)
                .build());
        String refreshToken = jwtTokenProvider.generateRefreshToken(member.getId().toString());
        member.updateRefreshToken(refreshToken);
        memberRepository.save(member);

        mockMvc.perform(post("/api/v1/auth/reissue")
                        .cookie(new Cookie("refreshToken", refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("AUTH_200_2"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(cookie().exists("refreshToken"));
    }

    @Test
    void Bearer_헤더의_accessToken으로_인증된다() throws Exception {
        Member member = memberWithProfile("2006");
        String accessToken = jwtTokenProvider.generateToken(member.getId().toString());

        mockMvc.perform(get("/api/v1/students/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    // 웹이 Bearer 헤더로 전환하기 전까지는 기존 accessToken 쿠키도 받아야 한다(#286에서 제거 예정)
    @Test
    void accessToken_쿠키로도_인증된다() throws Exception {
        Member member = memberWithProfile("2007");
        String accessToken = jwtTokenProvider.generateToken(member.getId().toString());

        mockMvc.perform(get("/api/v1/students/me")
                        .cookie(new Cookie("accessToken", accessToken)))
                .andExpect(status().isOk());
    }

    @Test
    void Bearer_헤더가_비어있으면_accessToken_쿠키로_인증된다() throws Exception {
        Member member = memberWithProfile("2008");
        String accessToken = jwtTokenProvider.generateToken(member.getId().toString());

        mockMvc.perform(get("/api/v1/students/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer ")
                        .cookie(new Cookie("accessToken", accessToken)))
                .andExpect(status().isOk());
    }

    @Test
    void 리프레시_토큰을_Bearer_헤더로_보내면_401_AUTH_001을_반환한다() throws Exception {
        String refreshToken = jwtTokenProvider.generateRefreshToken("1");

        mockMvc.perform(get("/api/v1/students/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + refreshToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_001"));
    }

    @Test
    void 로그아웃에_성공하면_DB_refreshToken이_null이_되고_인증쿠키가_삭제된다() throws Exception {
        Member member = memberRepository.save(Member.builder()
                .nickname("로그아웃회원")
                .oauthProvider(OauthProvider.KAKAO)
                .oauthId("3100")
                .email(null)
                .build());
        String refreshToken = jwtTokenProvider.generateRefreshToken(member.getId().toString());
        member.updateRefreshToken(refreshToken);
        memberRepository.save(member);

        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(new Cookie("refreshToken", refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("AUTH_200_1"))
                .andExpect(cookie().maxAge("refreshToken", 0))
                .andExpect(cookie().maxAge("accessToken", 0));

        Member reloaded = memberRepository.findById(member.getId()).orElseThrow();
        assertThat(reloaded.getRefreshToken()).isNull();
    }

    // 로그아웃은 실패할 이유가 없어야 한다 - 쿠키가 아예 없어도 200 + 쿠키 삭제 헤더를 내려준다.
    @Test
    void 쿠키_없이_로그아웃해도_200이고_쿠키_삭제_헤더를_내려준다() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("AUTH_200_1"))
                .andExpect(cookie().maxAge("refreshToken", 0))
                .andExpect(cookie().maxAge("accessToken", 0));
    }

    // 제시한 토큰이 DB에 저장된 토큰과 다르면(동시 재발급으로 이미 회전된 경우 등) DB는 건드리지 않는다.
    // 조건부 UPDATE(WHERE refresh_token = 제시토큰)가 어긋나 0건이 되어, 방금 갱신된 세션을 지우지 않는다.
    @Test
    void 저장된_토큰과_다른_refreshToken으로_로그아웃하면_DB는_그대로_두고_200이다() throws Exception {
        Member member = memberRepository.save(Member.builder()
                .nickname("불일치회원")
                .oauthProvider(OauthProvider.KAKAO)
                .oauthId("3101")
                .email(null)
                .build());
        String presentedToken = jwtTokenProvider.generateRefreshToken(member.getId().toString());
        // DB에는 제시 토큰과 다른 값이 저장돼 있는 상황을 만든다.
        member.updateRefreshToken("stored-different-refresh-token");
        memberRepository.save(member);

        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(new Cookie("refreshToken", presentedToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("AUTH_200_1"));

        Member reloaded = memberRepository.findById(member.getId()).orElseThrow();
        assertThat(reloaded.getRefreshToken()).isEqualTo("stored-different-refresh-token");
    }

    // refresh 타입이 아닌 토큰(accessToken 등)이나 깨진 토큰으로 로그아웃하면 DB는 건드리지 않고 200으로 끝낸다.
    @Test
    void refresh_타입이_아닌_토큰으로_로그아웃하면_DB는_그대로_두고_200이다() throws Exception {
        Member member = memberRepository.save(Member.builder()
                .nickname("타입불일치회원")
                .oauthProvider(OauthProvider.KAKAO)
                .oauthId("3103")
                .email(null)
                .build());
        String storedRefreshToken = jwtTokenProvider.generateRefreshToken(member.getId().toString());
        member.updateRefreshToken(storedRefreshToken);
        memberRepository.save(member);
        // refresh가 아니라 access 타입 토큰을 쿠키로 보낸다.
        String accessToken = jwtTokenProvider.generateToken(member.getId().toString());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(new Cookie("refreshToken", accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("AUTH_200_1"));

        Member reloaded = memberRepository.findById(member.getId()).orElseThrow();
        assertThat(reloaded.getRefreshToken()).isEqualTo(storedRefreshToken);
    }

    // 로그아웃으로 DB 토큰이 무효화되면, 같은 refreshToken으로 재발급을 시도해도 401(AUTH_005)이다.
    @Test
    void 로그아웃_후_같은_refreshToken으로_재발급하면_401_AUTH_005를_반환한다() throws Exception {
        Member member = memberRepository.save(Member.builder()
                .nickname("재로그아웃회원")
                .oauthProvider(OauthProvider.KAKAO)
                .oauthId("3102")
                .email(null)
                .build());
        String refreshToken = jwtTokenProvider.generateRefreshToken(member.getId().toString());
        member.updateRefreshToken(refreshToken);
        memberRepository.save(member);

        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(new Cookie("refreshToken", refreshToken)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/reissue")
                        .cookie(new Cookie("refreshToken", refreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_005"));
    }

    private Member memberWithProfile(String suffix) {
        School school = schoolRepository.save(School.builder().name("경희대학교 국제캠퍼스-" + suffix).build());
        Department department = departmentRepository.save(Department.builder()
                .school(school)
                .college("공과대학")
                .name("컴퓨터공학과")
                .build());
        Member member = memberRepository.save(Member.builder()
                .nickname("인증회원")
                .oauthProvider(OauthProvider.KAKAO)
                .oauthId(suffix)
                .email(null)
                .build());
        studentProfileRepository.save(StudentProfile.builder()
                .member(member)
                .school(school)
                .department(department)
                .admissionYear(2022)
                .build());
        return member;
    }

    private record LoginRequestFixture(String provider, String oauthAccessToken) {
    }
}