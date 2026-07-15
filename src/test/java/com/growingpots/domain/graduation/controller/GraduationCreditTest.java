package com.growingpots.domain.graduation.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.growingpots.domain.planner.entity.PlannerSimulation;
import com.growingpots.domain.planner.entity.PlannerTerm;
import com.growingpots.domain.planner.entity.PlannerTermVersion;
import com.growingpots.domain.planner.entity.PlannerVersionItem;
import com.growingpots.domain.planner.repository.PlannerSimulationRepository;
import com.growingpots.domain.planner.repository.PlannerTermRepository;
import com.growingpots.domain.planner.repository.PlannerTermVersionRepository;
import com.growingpots.domain.planner.repository.PlannerVersionItemRepository;
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
import com.growingpots.domain.university.entity.School;
import com.growingpots.domain.university.entity.enums.DivisionCategory;
import com.growingpots.domain.university.entity.enums.OpenedSemester;
import com.growingpots.domain.university.repository.CourseRepository;
import com.growingpots.domain.university.repository.DepartmentRepository;
import com.growingpots.domain.university.repository.DivisionRepository;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

// 총학점·카테고리별 학점 계산 정합성 검증.
// 핵심 불변식:
//   1. PDF 총학점은 이미 IN_PROGRESS를 포함한 값(extractTotalCredit 결과)이 스냅샷에 저장된다.
//   2. PLANNED 모드에서 delta는 신규 계획 과목(COMPLETED·IN_PROGRESS 아닌 것)에서만 온다.
//      → IN_PROGRESS 과목이 플래너에도 들어 있어도 이중 계산되지 않는다.
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class GraduationCreditTest {

    @Autowired MockMvc mockMvc;
    @Autowired SchoolRepository schoolRepository;
    @Autowired DepartmentRepository departmentRepository;
    @Autowired DivisionRepository divisionRepository;
    @Autowired CourseRepository courseRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired StudentProfileRepository studentProfileRepository;
    @Autowired StudentMajorRepository studentMajorRepository;
    @Autowired GraduationAnalysisSummaryRepository graduationAnalysisSummaryRepository;
    @Autowired StudentCourseRepository studentCourseRepository;
    @Autowired PlannerSimulationRepository plannerSimulationRepository;
    @Autowired PlannerTermRepository plannerTermRepository;
    @Autowired PlannerTermVersionRepository plannerTermVersionRepository;
    @Autowired PlannerVersionItemRepository plannerVersionItemRepository;

    private Authentication authOf(Long memberId) {
        return new UsernamePasswordAuthenticationToken(memberId.toString(), null, Collections.emptyList());
    }

    // ────────────────────────────────────────────────────────────────────────
    // 테스트 1: COMPLETED 모드 - 총학점은 PDF 스냅샷 값(IN_PROGRESS 포함 합계)이다
    // ────────────────────────────────────────────────────────────────────────

    // PDF에서 "44(62)" 형식으로 파싱된 총학점(62)이 그대로 응답에 나와야 한다.
    // IN_PROGRESS 과목(3학점)이 DB에 있어도 스냅샷에 이미 포함된 것이므로 다시 더해지지 않는다.
    @Test
    void COMPLETED_총학점은_PDF_스냅샷_값이며_IN_PROGRESS가_이중으로_더해지지_않는다() throws Exception {
        School school = schoolRepository.save(School.builder().name("경희대학교-8001").build());
        Department dept = departmentRepository.save(Department.builder()
                .school(school).college("공과대학").name("컴퓨터공학과-8001").build());
        Division majorElective = divisionRepository.save(Division.builder()
                .school(school).code("05-8001").category(DivisionCategory.MAJOR_ELECTIVE).build());
        Member member = memberRepository.save(Member.builder()
                .nickname("테스트").oauthProvider(OauthProvider.KAKAO).oauthId("8001").email(null).build());
        StudentProfile profile = studentProfileRepository.save(StudentProfile.builder()
                .member(member).school(school).department(dept).admissionYear(2023).build());
        StudentMajor major = studentMajorRepository.save(StudentMajor.builder()
                .studentProfile(profile).department(dept).majorType(MajorType.MAIN).build());

        // PDF 스냅샷: 총학점=62(이미 IN_PROGRESS 포함), 전공선택=6
        graduationAnalysisSummaryRepository.save(GraduationAnalysisSummary.builder()
                .studentMajor(major)
                .totalCreditCurrent(62).totalCreditRequired(130)
                .majorElectiveCurrent(6).majorElectiveRequired(18)
                .build());

        // IN_PROGRESS 과목 3학점 — 이미 스냅샷의 62에 포함된 상태
        Course inProgressCourse = courseRepository.save(Course.builder()
                .school(school).courseCode("CS101-8001").name("자료구조").credit(3)
                .offeringDepartment(dept).defaultDivision(majorElective)
                .recommendedYearLow(1).recommendedYearHigh(2).openedSemester(OpenedSemester.FIRST)
                .isEnglish(false).isSw(false).isActive(true).build());
        studentCourseRepository.save(StudentCourse.builder()
                .studentProfile(profile).course(inProgressCourse)
                .rawCourseCode("CS101-8001").rawCourseName("자료구조")
                .credit(3).takenYear(2024).takenSemester(Semester.FIRST)
                .appliedDivision(majorElective)
                .status(CourseStatus.IN_PROGRESS).source(RecordSource.PDF).isRetake(false).build());

        // COMPLETED: 총학점 = 스냅샷 62 그대로 (IN_PROGRESS 3학점을 다시 더하면 안 됨)
        mockMvc.perform(get("/api/v1/students/me/graduation")
                        .param("studentMajorId", String.valueOf(major.getId()))
                        .param("source", "COMPLETED")
                        .with(authentication(authOf(member.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.totalCredits.current").value(62));
    }

    // ────────────────────────────────────────────────────────────────────────
    // 테스트 2: PLANNED 모드 - 신규 계획 과목만 총학점 delta로 더해진다
    // ────────────────────────────────────────────────────────────────────────

    // IN_PROGRESS 과목이 플래너에도 담겨 있어도 `alreadyCounted`로 걸러져 delta에서 제외된다.
    // 오직 아직 수강 이력이 없는 신규 계획 과목만 총학점에 더해진다.
    //
    // 세팅:
    //   - PDF 스냅샷 총학점: 62 (IN_PROGRESS 3학점 포함)
    //   - course A (IN_PROGRESS, 3학점) → alreadyCounted에 포함 → 플래너 item이어도 delta 제외
    //   - course B (수강 이력 없음, 3학점) → 신규 계획 → delta +3
    //
    // 기대:
    //   - COMPLETED: 62
    //   - PLANNED:   62 + 3 = 65  (course A가 이중으로 더해지면 68이 되는데 그래서는 안 됨)
    @Test
    void PLANNED_총학점은_스냅샷에_신규계획과목만_더해지고_IN_PROGRESS는_이중계산되지_않는다() throws Exception {
        School school = schoolRepository.save(School.builder().name("경희대학교-8002").build());
        Department dept = departmentRepository.save(Department.builder()
                .school(school).college("공과대학").name("컴퓨터공학과-8002").build());
        Division majorElective = divisionRepository.save(Division.builder()
                .school(school).code("05-8002").category(DivisionCategory.MAJOR_ELECTIVE).build());
        Member member = memberRepository.save(Member.builder()
                .nickname("테스트").oauthProvider(OauthProvider.KAKAO).oauthId("8002").email(null).build());
        StudentProfile profile = studentProfileRepository.save(StudentProfile.builder()
                .member(member).school(school).department(dept).admissionYear(2023).build());
        StudentMajor major = studentMajorRepository.save(StudentMajor.builder()
                .studentProfile(profile).department(dept).majorType(MajorType.MAIN).build());

        graduationAnalysisSummaryRepository.save(GraduationAnalysisSummary.builder()
                .studentMajor(major)
                .totalCreditCurrent(62).totalCreditRequired(130)
                .majorElectiveCurrent(3).majorElectiveRequired(18)
                .build());

        // course A: IN_PROGRESS — alreadyCounted에 들어감
        Course courseA = courseRepository.save(Course.builder()
                .school(school).courseCode("CS201-8002").name("알고리즘").credit(3)
                .offeringDepartment(dept).defaultDivision(majorElective)
                .recommendedYearLow(2).recommendedYearHigh(3).openedSemester(OpenedSemester.FIRST)
                .isEnglish(false).isSw(false).isActive(true).build());
        studentCourseRepository.save(StudentCourse.builder()
                .studentProfile(profile).course(courseA)
                .rawCourseCode("CS201-8002").rawCourseName("알고리즘")
                .credit(3).takenYear(2024).takenSemester(Semester.FIRST)
                .appliedDivision(majorElective)
                .status(CourseStatus.IN_PROGRESS).source(RecordSource.PDF).isRetake(false).build());

        // course B: 수강 이력 없는 신규 과목
        Course courseB = courseRepository.save(Course.builder()
                .school(school).courseCode("CS202-8002").name("운영체제").credit(3)
                .offeringDepartment(dept).defaultDivision(majorElective)
                .recommendedYearLow(2).recommendedYearHigh(3).openedSemester(OpenedSemester.FIRST)
                .isEnglish(false).isSw(false).isActive(true).build());

        // 플래너: course A(IN_PROGRESS)와 course B(신규) 둘 다 담겨 있음
        PlannerSimulation simulation = plannerSimulationRepository.save(PlannerSimulation.builder()
                .studentProfile(profile).name("플래너-8002").build());
        PlannerTerm term = plannerTermRepository.save(PlannerTerm.builder()
                .plannerSimulation(simulation).yearLevel(3).semester(1).build());
        PlannerTermVersion version = plannerTermVersionRepository.save(PlannerTermVersion.builder()
                .plannerTerm(term).versionNo(1).name("폴더1").isSelected(true).versionOrder(0).build());
        plannerVersionItemRepository.save(PlannerVersionItem.builder()
                .plannerTermVersion(version).course(courseA).plannedDivision(majorElective)
                .credit(3).coursePositionOrder(0).build());
        plannerVersionItemRepository.save(PlannerVersionItem.builder()
                .plannerTermVersion(version).course(courseB).plannedDivision(majorElective)
                .credit(3).coursePositionOrder(1).build());

        mockMvc.perform(get("/api/v1/students/me/graduation")
                        .param("studentMajorId", String.valueOf(major.getId()))
                        .param("source", "COMPLETED")
                        .with(authentication(authOf(member.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.totalCredits.current").value(62));

        // PLANNED: course A는 alreadyCounted로 걸러짐 → delta = course B의 3학점만
        // 62 + 3 = 65. course A가 이중 반영되면 68이 되므로, 65인지 검증이 핵심.
        mockMvc.perform(get("/api/v1/students/me/graduation")
                        .param("studentMajorId", String.valueOf(major.getId()))
                        .param("source", "PLANNED")
                        .with(authentication(authOf(member.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.totalCredits.current").value(65));
    }

    // ────────────────────────────────────────────────────────────────────────
    // 테스트 3: PLANNED 모드 - 카테고리별 학점 delta도 신규 계획 과목에서만 온다
    // ────────────────────────────────────────────────────────────────────────

    // IN_PROGRESS 과목의 카테고리 학점은 PDF 스냅샷에 이미 반영됐으므로, PLANNED delta에서도
    // IN_PROGRESS는 건너뛰고 신규 계획 과목만 더해야 한다.
    //
    // 세팅:
    //   - PDF 스냅샷 전공필수(MAJOR_REQUIRED) = 20 (IN_PROGRESS 3학점 포함)
    //   - course A (IN_PROGRESS, MAJOR_REQUIRED, 3학점) → 스냅샷 20에 이미 포함
    //   - course B (신규 계획, MAJOR_REQUIRED, 3학점)
    //
    // 기대:
    //   - COMPLETED: MAJOR_REQUIRED.current = 20
    //   - PLANNED:   MAJOR_REQUIRED.current = 23 (course A가 이중 반영되면 26)
    @Test
    void PLANNED_카테고리별_학점은_신규계획과목만_delta로_반영된다() throws Exception {
        School school = schoolRepository.save(School.builder().name("경희대학교-8003").build());
        Department dept = departmentRepository.save(Department.builder()
                .school(school).college("공과대학").name("컴퓨터공학과-8003").build());
        Division majorRequired = divisionRepository.save(Division.builder()
                .school(school).code("04-8003").category(DivisionCategory.MAJOR_REQUIRED).build());
        Member member = memberRepository.save(Member.builder()
                .nickname("테스트").oauthProvider(OauthProvider.KAKAO).oauthId("8003").email(null).build());
        StudentProfile profile = studentProfileRepository.save(StudentProfile.builder()
                .member(member).school(school).department(dept).admissionYear(2023).build());
        StudentMajor major = studentMajorRepository.save(StudentMajor.builder()
                .studentProfile(profile).department(dept).majorType(MajorType.MAIN).build());

        // 스냅샷: 전공필수=20 (IN_PROGRESS 3학점이 이미 포함된 값)
        graduationAnalysisSummaryRepository.save(GraduationAnalysisSummary.builder()
                .studentMajor(major)
                .totalCreditCurrent(62).totalCreditRequired(130)
                .majorRequiredCurrent(20).majorRequiredRequired(30)
                .build());

        // course A: IN_PROGRESS — 스냅샷 전공필수 20에 이미 포함됨
        Course courseA = courseRepository.save(Course.builder()
                .school(school).courseCode("CS301-8003").name("컴파일러").credit(3)
                .offeringDepartment(dept).defaultDivision(majorRequired)
                .recommendedYearLow(3).recommendedYearHigh(4).openedSemester(OpenedSemester.FIRST)
                .isEnglish(false).isSw(false).isActive(true).build());
        studentCourseRepository.save(StudentCourse.builder()
                .studentProfile(profile).course(courseA)
                .rawCourseCode("CS301-8003").rawCourseName("컴파일러")
                .credit(3).takenYear(2024).takenSemester(Semester.FIRST)
                .appliedDivision(majorRequired)
                .status(CourseStatus.IN_PROGRESS).source(RecordSource.PDF).isRetake(false).build());

        // course B: 수강 이력 없는 신규 과목 — PLANNED delta 대상
        Course courseB = courseRepository.save(Course.builder()
                .school(school).courseCode("CS302-8003").name("네트워크").credit(3)
                .offeringDepartment(dept).defaultDivision(majorRequired)
                .recommendedYearLow(3).recommendedYearHigh(4).openedSemester(OpenedSemester.FIRST)
                .isEnglish(false).isSw(false).isActive(true).build());

        PlannerSimulation simulation = plannerSimulationRepository.save(PlannerSimulation.builder()
                .studentProfile(profile).name("플래너-8003").build());
        PlannerTerm term = plannerTermRepository.save(PlannerTerm.builder()
                .plannerSimulation(simulation).yearLevel(3).semester(2).build());
        PlannerTermVersion version = plannerTermVersionRepository.save(PlannerTermVersion.builder()
                .plannerTerm(term).versionNo(1).name("폴더1").isSelected(true).versionOrder(0).build());
        // course A도 플래너에 담겨 있지만 alreadyCounted로 걸러져야 함
        plannerVersionItemRepository.save(PlannerVersionItem.builder()
                .plannerTermVersion(version).course(courseA).plannedDivision(majorRequired)
                .credit(3).coursePositionOrder(0).build());
        plannerVersionItemRepository.save(PlannerVersionItem.builder()
                .plannerTermVersion(version).course(courseB).plannedDivision(majorRequired)
                .credit(3).coursePositionOrder(1).build());

        mockMvc.perform(get("/api/v1/students/me/graduation")
                        .param("studentMajorId", String.valueOf(major.getId()))
                        .param("source", "COMPLETED")
                        .with(authentication(authOf(member.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.conditions[?(@.code=='MAJOR_REQUIRED')].current").value(20));

        // PLANNED: course A는 걸러짐 → delta = course B(3학점)만
        // 20 + 3 = 23. course A가 이중 반영되면 26이 되므로, 23인지 검증이 핵심.
        mockMvc.perform(get("/api/v1/students/me/graduation")
                        .param("studentMajorId", String.valueOf(major.getId()))
                        .param("source", "PLANNED")
                        .with(authentication(authOf(member.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.conditions[?(@.code=='MAJOR_REQUIRED')].current").value(23));
    }
}
