package com.growingpots.domain.graduation.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.growingpots.domain.transcript.entity.GraduationAnalysisSummary;
import com.growingpots.domain.transcript.entity.StudentCourse;
import com.growingpots.domain.transcript.entity.enums.CourseStatus;
import com.growingpots.domain.transcript.entity.enums.RecordSource;
import com.growingpots.domain.transcript.entity.enums.Semester;
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

// 배분이수교과 영역 판정(2024학번+는 5개 영역 중 3개 이상 이수) 테스트.
// computeDistributedGeAreas() 로직을 GE 탭 API 호출로 검증한다.
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class GraduationDistributedGeTest {

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

    private Authentication authOf(Long memberId) {
        return new UsernamePasswordAuthenticationToken(memberId.toString(), null, Collections.emptyList());
    }

    // 학교별 GeArea(AREA_1~5) + DISTRIBUTED_GE Division 생성. 반환된 Division을 StudentCourse에 적용한다.
    private Division setupDistributedGeEnv(School school) {
        for (int i = 1; i <= 5; i++) {
            geAreaRepository.save(GeArea.builder().school(school).code("AREA_" + i).name("영역" + i).build());
        }
        return divisionRepository.save(Division.builder()
                .school(school).code("DIST_GE").category(DivisionCategory.DISTRIBUTED_GE).build());
    }

    // 특정 영역 코드의 과목을 하나 이수 처리. courseCode에 suffix를 붙여 테스트 간 중복을 피한다.
    private void takeCourseInArea(StudentProfile profile, Division distGeDiv, String areaCode, String courseCodeSuffix) {
        GeArea area = geAreaRepository.findBySchool(profile.getSchool()).stream()
                .filter(a -> areaCode.equals(a.getCode())).findFirst().orElseThrow();
        Course course = courseRepository.save(Course.builder()
                .school(profile.getSchool()).courseCode("GE-" + areaCode + "-" + courseCodeSuffix)
                .name(areaCode + "과목").credit(3)
                .recommendedYearLow(1).recommendedYearHigh(4).openedSemester(OpenedSemester.FIRST)
                .isEnglish(false).isSw(false).isActive(true)
                .geArea(area).build());
        studentCourseRepository.save(StudentCourse.builder()
                .studentProfile(profile).course(course)
                .rawCourseCode(course.getCourseCode()).rawCourseName(course.getName())
                .credit(3).takenYear(2024).takenSemester(Semester.FIRST)
                .appliedDivision(distGeDiv)
                .status(CourseStatus.COMPLETED).source(RecordSource.PDF).isRetake(false).build());
    }

    // 기본 학생 생성 + GeArea 5개 + DISTRIBUTED_GE Division 셋업.
    private record GeTestEnv(StudentMajor major, StudentProfile profile, Division distGeDiv) {}

    private GeTestEnv createGeStudent(String suffix, int admissionYear) {
        School school = schoolRepository.save(School.builder().name("경희대학교-" + suffix).build());
        Department dept = departmentRepository.save(Department.builder()
                .school(school).college("공과대학").name("컴퓨터공학과-" + suffix).build());
        Member member = memberRepository.save(Member.builder()
                .nickname("테스트").oauthProvider(OauthProvider.KAKAO).oauthId(suffix).email(null).build());
        StudentProfile profile = studentProfileRepository.save(StudentProfile.builder()
                .member(member).school(school).department(dept).admissionYear(admissionYear).build());
        StudentMajor major = studentMajorRepository.save(StudentMajor.builder()
                .studentProfile(profile).department(dept).majorType(MajorType.MAIN).build());
        Division distGeDiv = setupDistributedGeEnv(school);
        return new GeTestEnv(major, profile, distGeDiv);
    }

    // DISTRIBUTED_GE 조건만 의미 있는 요약 저장. 나머지 학점은 0/0으로 두면 모두 "0 >= 0" 충족으로 처리되어
    // DISTRIBUTED_GE 하나만 독립 검증할 수 있다.
    private void saveDistGeSummary(StudentMajor major, int current, int required) {
        graduationAnalysisSummaryRepository.save(GraduationAnalysisSummary.builder()
                .studentMajor(major)
                .distributedGeCurrent(current).distributedGeRequired(required)
                .build());
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // 영역 수 판정
    // ──────────────────────────────────────────────────────────────────────────────

    @Test
    void 영역_3개_이수하면_배분이수교과가_satisfied다() throws Exception {
        GeTestEnv env = createGeStudent("DG001", 2024);
        saveDistGeSummary(env.major(), 9, 9);
        takeCourseInArea(env.profile(), env.distGeDiv(), "AREA_1", "DG001");
        takeCourseInArea(env.profile(), env.distGeDiv(), "AREA_2", "DG001");
        takeCourseInArea(env.profile(), env.distGeDiv(), "AREA_3", "DG001");

        mockMvc.perform(get("/api/v1/students/me/graduation").param("majorType", "GE")
                        .with(authentication(authOf(env.profile().getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.conditions[?(@.code=='DISTRIBUTED_GE')].satisfied").value(true));
    }

    @Test
    void 영역_2개만_이수하면_학점이_충분해도_satisfied가_아니다() throws Exception {
        GeTestEnv env = createGeStudent("DG002", 2024);
        // 학점: 2개 영역 × 3학점 = 6 >= 6 (충족), 영역: 2개 < 3 (미충족)
        saveDistGeSummary(env.major(), 6, 6);
        takeCourseInArea(env.profile(), env.distGeDiv(), "AREA_1", "DG002");
        takeCourseInArea(env.profile(), env.distGeDiv(), "AREA_2", "DG002");

        mockMvc.perform(get("/api/v1/students/me/graduation").param("majorType", "GE")
                        .with(authentication(authOf(env.profile().getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.conditions[?(@.code=='DISTRIBUTED_GE')].satisfied").value(false));
    }

    @Test
    void 한_영역에_과목을_몰아이수해도_영역_수가_1개면_satisfied가_아니다() throws Exception {
        GeTestEnv env = createGeStudent("DG003", 2024);
        // AREA_1에서 과목 4개 이수 → 학점 12 >= 9 (충족), 영역 1개 < 3 (미충족)
        List<String> areaCodes = List.of("AREA_1", "AREA_1", "AREA_1", "AREA_1");
        for (int i = 0; i < areaCodes.size(); i++) {
            takeCourseInArea(env.profile(), env.distGeDiv(), areaCodes.get(i), "DG003-" + i);
        }
        saveDistGeSummary(env.major(), 12, 9);

        mockMvc.perform(get("/api/v1/students/me/graduation").param("majorType", "GE")
                        .with(authentication(authOf(env.profile().getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.conditions[?(@.code=='DISTRIBUTED_GE')].satisfied").value(false));
    }

    // 경계값: 5개 영역 중 정확히 3개 = 충족 기준
    @Test
    void 비연속_영역이라도_3개_영역_이수하면_satisfied다() throws Exception {
        GeTestEnv env = createGeStudent("DG004", 2024);
        saveDistGeSummary(env.major(), 9, 9);
        // AREA_1, AREA_3, AREA_5 (비연속)
        takeCourseInArea(env.profile(), env.distGeDiv(), "AREA_1", "DG004");
        takeCourseInArea(env.profile(), env.distGeDiv(), "AREA_3", "DG004");
        takeCourseInArea(env.profile(), env.distGeDiv(), "AREA_5", "DG004");

        mockMvc.perform(get("/api/v1/students/me/graduation").param("majorType", "GE")
                        .with(authentication(authOf(env.profile().getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.conditions[?(@.code=='DISTRIBUTED_GE')].satisfied").value(true));
    }

    @Test
    void 배분이수_이수_과목이_없으면_satisfied가_아니다() throws Exception {
        GeTestEnv env = createGeStudent("DG005", 2024);
        // StudentCourse 없음 → 영역 0개, 학점 0 < 9
        saveDistGeSummary(env.major(), 0, 9);

        mockMvc.perform(get("/api/v1/students/me/graduation").param("majorType", "GE")
                        .with(authentication(authOf(env.profile().getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.conditions[?(@.code=='DISTRIBUTED_GE')].satisfied").value(false));
    }

    // 영역은 3개를 채웠지만 학점 스냅샷이 미달인 경우 — 학점 AND 영역 둘 다 충족해야 한다
    @Test
    void 영역_3개여도_학점이_부족하면_satisfied가_아니다() throws Exception {
        GeTestEnv env = createGeStudent("DG006", 2024);
        // 영역: 3개 (충족), 학점: 9 < 12 (미충족)
        saveDistGeSummary(env.major(), 9, 12);
        takeCourseInArea(env.profile(), env.distGeDiv(), "AREA_1", "DG006");
        takeCourseInArea(env.profile(), env.distGeDiv(), "AREA_2", "DG006");
        takeCourseInArea(env.profile(), env.distGeDiv(), "AREA_3", "DG006");

        mockMvc.perform(get("/api/v1/students/me/graduation").param("majorType", "GE")
                        .with(authentication(authOf(env.profile().getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.conditions[?(@.code=='DISTRIBUTED_GE')].satisfied").value(false));
    }

    // 2023학번 이하는 영역 판정이 없어 학점만으로 satisfied를 결정한다.
    // 영역이 1개뿐이어도 학점 기준만 충족하면 satisfied=true가 나와야 한다.
    @Test
    void 학번_2023이면_영역_판정_없이_학점만_충족하면_satisfied다() throws Exception {
        GeTestEnv env = createGeStudent("DG007", 2023);
        saveDistGeSummary(env.major(), 9, 9);
        // AREA_1 하나에만 3과목 이수 → 학점 충족, 영역 1개뿐
        takeCourseInArea(env.profile(), env.distGeDiv(), "AREA_1", "DG007-0");
        takeCourseInArea(env.profile(), env.distGeDiv(), "AREA_1", "DG007-1");
        takeCourseInArea(env.profile(), env.distGeDiv(), "AREA_1", "DG007-2");

        mockMvc.perform(get("/api/v1/students/me/graduation").param("majorType", "GE")
                        .with(authentication(authOf(env.profile().getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.conditions[?(@.code=='DISTRIBUTED_GE')].satisfied").value(true));
    }
}