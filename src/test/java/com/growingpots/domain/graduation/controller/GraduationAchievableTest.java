package com.growingpots.domain.graduation.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.growingpots.domain.transcript.entity.CertResult;
import com.growingpots.domain.transcript.entity.GraduationAnalysisSummary;
import com.growingpots.domain.transcript.entity.StudentCourse;
import com.growingpots.domain.transcript.entity.enums.CertJudgement;
import com.growingpots.domain.transcript.entity.enums.CertType;
import com.growingpots.domain.transcript.entity.enums.CourseStatus;
import com.growingpots.domain.transcript.entity.enums.RecordSource;
import com.growingpots.domain.transcript.entity.enums.Semester;
import com.growingpots.domain.transcript.repository.CertResultRepository;
import com.growingpots.domain.transcript.repository.GraduationAnalysisSummaryRepository;
import com.growingpots.domain.transcript.repository.StudentCourseRepository;
import com.growingpots.domain.university.entity.Course;
import com.growingpots.domain.university.entity.Department;
import com.growingpots.domain.university.entity.Division;
import com.growingpots.domain.university.entity.GeArea;
import com.growingpots.domain.university.entity.School;
import com.growingpots.domain.university.entity.enums.DivisionCategory;
import com.growingpots.domain.university.entity.enums.OpenedSemester;
import com.growingpots.domain.university.repository.CourseRepository;
import com.growingpots.domain.university.repository.DepartmentRepository;
import com.growingpots.domain.university.repository.DivisionRepository;
import com.growingpots.domain.university.repository.GeAreaRepository;
import com.growingpots.domain.university.repository.SchoolRepository;
import com.growingpots.domain.user.entity.Member;
import com.growingpots.domain.user.entity.StudentMajor;
import com.growingpots.domain.user.entity.StudentMajor.MajorType;
import com.growingpots.domain.user.entity.StudentProfile;
import com.growingpots.domain.user.entity.enums.OauthProvider;
import com.growingpots.domain.user.repository.MemberRepository;
import com.growingpots.domain.user.repository.StudentMajorRepository;
import com.growingpots.domain.user.repository.StudentProfileRepository;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

// computeGraduatable() 종합 판정 테스트.
// 2024학번을 기준으로 배분이수 영역 판정까지 모두 경로에 포함시킨 뒤,
// 전공학점 / 교양학점 / 영역 수 / 영어 / SW / 평점 / 비학점 인증 요소를 하나씩 깨뜨려 graduatable=false를 확인한다.
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class GraduationAchievableTest {

    @Autowired MockMvc mockMvc;
    @Autowired SchoolRepository schoolRepository;
    @Autowired DepartmentRepository departmentRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired StudentProfileRepository studentProfileRepository;
    @Autowired StudentMajorRepository studentMajorRepository;
    @Autowired GraduationAnalysisSummaryRepository graduationAnalysisSummaryRepository;
    @Autowired GeAreaRepository geAreaRepository;
    @Autowired DivisionRepository divisionRepository;
    @Autowired CourseRepository courseRepository;
    @Autowired StudentCourseRepository studentCourseRepository;
    @Autowired CertResultRepository certResultRepository;

    private Authentication authOf(Long memberId) {
        return new UsernamePasswordAuthenticationToken(memberId.toString(), null, Collections.emptyList());
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 픽스처 헬퍼
    // ─────────────────────────────────────────────────────────────────────────────

    private record TestEnv(StudentMajor major, StudentProfile profile) {}

    // 2024학번 학생 + AREA_1~5 GeArea + DISTRIBUTED_GE Division 생성.
    // areaCount개의 영역에 각 1과목씩 이수 처리한다. (3 → 영역 충족, 2 → 영역 미충족)
    private TestEnv createStudent(String suffix, int areaCount) {
        School school = schoolRepository.save(School.builder().name("경희대학교-" + suffix).build());
        Department dept = departmentRepository.save(Department.builder()
                .school(school).college("공과대학").name("컴퓨터공학과-" + suffix).build());
        Member member = memberRepository.save(Member.builder()
                .nickname("테스트").oauthProvider(OauthProvider.KAKAO).oauthId(suffix).email(null).build());
        StudentProfile profile = studentProfileRepository.save(StudentProfile.builder()
                .member(member).school(school).department(dept).admissionYear(2024).build());
        StudentMajor major = studentMajorRepository.save(StudentMajor.builder()
                .studentProfile(profile).department(dept).majorType(MajorType.MAIN).build());

        List<GeArea> areas = List.of(
                geAreaRepository.save(GeArea.builder().school(school).code("AREA_1").name("영역1").build()),
                geAreaRepository.save(GeArea.builder().school(school).code("AREA_2").name("영역2").build()),
                geAreaRepository.save(GeArea.builder().school(school).code("AREA_3").name("영역3").build()),
                geAreaRepository.save(GeArea.builder().school(school).code("AREA_4").name("영역4").build()),
                geAreaRepository.save(GeArea.builder().school(school).code("AREA_5").name("영역5").build())
        );
        Division distGeDiv = divisionRepository.save(Division.builder()
                .school(school).code("DIST_GE").category(DivisionCategory.DISTRIBUTED_GE).build());

        for (int i = 0; i < areaCount; i++) {
            Course course = courseRepository.save(Course.builder()
                    .school(school).courseCode("GE-" + suffix + "-" + i)
                    .name("배분이수과목" + i).credit(3)
                    .recommendedYearLow(1).recommendedYearHigh(4).openedSemester(OpenedSemester.FIRST)
                    .isEnglish(false).isSw(false).isActive(true)
                    .geArea(areas.get(i)).build());
            studentCourseRepository.save(StudentCourse.builder()
                    .studentProfile(profile).course(course)
                    .rawCourseCode(course.getCourseCode()).rawCourseName(course.getName())
                    .credit(3).takenYear(2024).takenSemester(Semester.FIRST)
                    .appliedDivision(distGeDiv)
                    .status(CourseStatus.COMPLETED).source(RecordSource.PDF).isRetake(false).build());
        }
        return new TestEnv(major, profile);
    }

    // 모든 학점 요건이 current == required인 완벽한 요약 빌더.
    // 특정 항목만 바꿀 때는 반환된 빌더에 해당 필드를 다시 호출하면 덮어쓰인다.
    private GraduationAnalysisSummary.GraduationAnalysisSummaryBuilder perfectSummary(StudentMajor major) {
        return GraduationAnalysisSummary.builder()
                .studentMajor(major)
                .majorBasicCurrent(15).majorBasicRequired(15)
                .majorRequiredCurrent(20).majorRequiredRequired(20)
                .majorElectiveCurrent(18).majorElectiveRequired(18)
                .requiredPlusElectiveCurrent(38).requiredPlusElectiveRequired(38)
                .requiredGeCurrent(30).requiredGeRequired(30)
                .distributedGeCurrent(9).distributedGeRequired(9)
                .freeGeCurrent(18).freeGeRequired(18)
                .generalElectiveCurrent(6)
                .englishCurrent(4).englishRequired(4)
                .swCertCurrent(6).swCertRequired(6)
                .gpaCurrent(new BigDecimal("3.000")).gpaRequired(new BigDecimal("2.000"))
                .totalCreditCurrent(130).totalCreditRequired(130);
    }

    private void assertGraduatable(TestEnv env, boolean expected) throws Exception {
        mockMvc.perform(get("/api/v1/students/me/graduation")
                        .with(authentication(authOf(env.profile().getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.graduatable").value(expected));
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 기준선: 모든 요건 동시 충족
    // ─────────────────────────────────────────────────────────────────────────────

    // 전공학점 + 교양학점 + 배분이수 영역 3개 + 영어 + SW + GPA + 비학점 인증 없음 → true
    @Test
    void 모든_요건을_동시에_충족하면_graduatable이_true다() throws Exception {
        TestEnv env = createStudent("GA001", 3);
        graduationAnalysisSummaryRepository.save(perfectSummary(env.major()).build());

        assertGraduatable(env, true);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 전공 학점 요건 (각각 독립적으로 졸업 차단)
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    void 전공기초_학점이_1학점_부족하면_나머지_충족해도_graduatable이_false다() throws Exception {
        TestEnv env = createStudent("GA002", 3);
        graduationAnalysisSummaryRepository.save(perfectSummary(env.major())
                .majorBasicCurrent(14).majorBasicRequired(15).build());

        assertGraduatable(env, false);
    }

    @Test
    void 전공필수_학점이_부족하면_graduatable이_false다() throws Exception {
        TestEnv env = createStudent("GA003", 3);
        graduationAnalysisSummaryRepository.save(perfectSummary(env.major())
                .majorRequiredCurrent(19).majorRequiredRequired(20).build());

        assertGraduatable(env, false);
    }

    @Test
    void 전공선택_학점이_부족하면_graduatable이_false다() throws Exception {
        TestEnv env = createStudent("GA004", 3);
        graduationAnalysisSummaryRepository.save(perfectSummary(env.major())
                .majorElectiveCurrent(17).majorElectiveRequired(18).build());

        assertGraduatable(env, false);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 교양 학점 요건
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    void 필수교과_학점이_부족하면_graduatable이_false다() throws Exception {
        TestEnv env = createStudent("GA005", 3);
        graduationAnalysisSummaryRepository.save(perfectSummary(env.major())
                .requiredGeCurrent(29).requiredGeRequired(30).build());

        assertGraduatable(env, false);
    }

    @Test
    void 배분이수_학점이_부족하면_graduatable이_false다() throws Exception {
        TestEnv env = createStudent("GA006", 3);
        // 영역은 3개 충족, 학점만 1 모자람
        graduationAnalysisSummaryRepository.save(perfectSummary(env.major())
                .distributedGeCurrent(8).distributedGeRequired(9).build());

        assertGraduatable(env, false);
    }

    // 학점 스냅샷은 충족(6 >= 6)이지만 실제 이수 영역이 2개뿐 → geAreaResult.satisfied=false
    @Test
    void 배분이수_학점_충족해도_영역_수가_2개면_graduatable이_false다() throws Exception {
        TestEnv env = createStudent("GA007", 2);
        graduationAnalysisSummaryRepository.save(perfectSummary(env.major())
                .distributedGeCurrent(6).distributedGeRequired(6).build());

        assertGraduatable(env, false);
    }

    @Test
    void 자유이수_학점이_부족하면_graduatable이_false다() throws Exception {
        TestEnv env = createStudent("GA008", 3);
        graduationAnalysisSummaryRepository.save(perfectSummary(env.major())
                .freeGeCurrent(17).freeGeRequired(18).build());

        assertGraduatable(env, false);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 영어 강의 수 요건
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    void 영어_강의_수가_1개_부족하면_graduatable이_false다() throws Exception {
        TestEnv env = createStudent("GA009", 3);
        graduationAnalysisSummaryRepository.save(perfectSummary(env.major())
                .englishCurrent(3).englishRequired(4).build());

        assertGraduatable(env, false);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // SW 인증 학점 요건
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    void SW_학점이_부족하면_graduatable이_false다() throws Exception {
        TestEnv env = createStudent("GA010", 3);
        graduationAnalysisSummaryRepository.save(perfectSummary(env.major())
                .swCertCurrent(5).swCertRequired(6).build());

        assertGraduatable(env, false);
    }

    // swCertRequired=null → 이 학과엔 SW 인증 요건 자체가 없음 → 졸업 차단 안 됨
    @Test
    void swCertRequired가_null이면_SW_요건_없어_graduatable이_true다() throws Exception {
        TestEnv env = createStudent("GA011", 3);
        graduationAnalysisSummaryRepository.save(perfectSummary(env.major())
                .swCertRequired(null).swCertCurrent(null).build());

        assertGraduatable(env, true);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 평점(GPA) 요건
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    void GPA가_기준_미달이면_graduatable이_false다() throws Exception {
        TestEnv env = createStudent("GA012", 3);
        graduationAnalysisSummaryRepository.save(perfectSummary(env.major())
                .gpaCurrent(new BigDecimal("1.999")).gpaRequired(new BigDecimal("2.000")).build());

        assertGraduatable(env, false);
    }

    // 경계값: gpaCurrent == gpaRequired → 충족
    @Test
    void GPA가_기준과_정확히_같으면_graduatable이_true다() throws Exception {
        TestEnv env = createStudent("GA013", 3);
        graduationAnalysisSummaryRepository.save(perfectSummary(env.major())
                .gpaCurrent(new BigDecimal("2.000")).gpaRequired(new BigDecimal("2.000")).build());

        assertGraduatable(env, true);
    }

    // gpaRequired=null → 이 학과엔 평점 요건 자체가 없음 → 졸업 차단 안 됨
    @Test
    void gpaRequired가_null이면_평점_요건_없어_graduatable이_true다() throws Exception {
        TestEnv env = createStudent("GA014", 3);
        graduationAnalysisSummaryRepository.save(perfectSummary(env.major())
                .gpaCurrent(new BigDecimal("1.000")).gpaRequired(null).build());

        assertGraduatable(env, true);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 비학점 인증 요건 (CertResult)
    // ─────────────────────────────────────────────────────────────────────────────

    // CertResult FAIL이 하나라도 있으면 모든 학점 요건을 충족해도 졸업 불가
    @Test
    void CertResult_FAIL이_있으면_나머지_전부_충족해도_graduatable이_false다() throws Exception {
        TestEnv env = createStudent("GA015", 3);
        graduationAnalysisSummaryRepository.save(perfectSummary(env.major()).build());
        certResultRepository.save(CertResult.builder()
                .studentProfile(env.profile()).studentMajor(env.major())
                .certType(CertType.THESIS).result(CertJudgement.FAIL)
                .source(RecordSource.PDF).build());

        assertGraduatable(env, false);
    }

    // PASS·EXEMPT·NONE은 졸업을 차단하지 않는다
    @Test
    void CertResult_PASS_EXEMPT_NONE은_graduatable을_차단하지_않는다() throws Exception {
        TestEnv env = createStudent("GA016", 3);
        graduationAnalysisSummaryRepository.save(perfectSummary(env.major()).build());
        certResultRepository.save(CertResult.builder()
                .studentProfile(env.profile()).studentMajor(env.major())
                .certType(CertType.THESIS).result(CertJudgement.PASS)
                .source(RecordSource.PDF).build());
        certResultRepository.save(CertResult.builder()
                .studentProfile(env.profile()).studentMajor(env.major())
                .certType(CertType.ENGLISH).result(CertJudgement.EXEMPT)
                .source(RecordSource.PDF).build());
        certResultRepository.save(CertResult.builder()
                .studentProfile(env.profile()).studentMajor(env.major())
                .certType(CertType.TOPIK).result(CertJudgement.NONE)
                .source(RecordSource.PDF).build());

        assertGraduatable(env, true);
    }
}