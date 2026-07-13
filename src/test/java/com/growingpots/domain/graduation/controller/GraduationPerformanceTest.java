package com.growingpots.domain.graduation.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import java.math.BigDecimal;
import java.util.ArrayList;
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

// getGraduation() API 응답 시간 측정.
// 2024학번 + 본전공/복수전공 각 1개 + StudentCourse 51개 기준으로 측정한다.
// WARMUP_ROUNDS: JVM JIT 안정화용. MEASURE_ROUNDS: 실측 횟수.
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class GraduationPerformanceTest {

    private static final int WARMUP_ROUNDS = 3;
    private static final int MEASURE_ROUNDS = 10;

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

    @Test
    void getGraduation_ALL탭_응답시간_측정() throws Exception {
        School school = schoolRepository.save(School.builder().name("경희대학교 성능테스트").build());

        Department mainDept = departmentRepository.save(Department.builder()
                .school(school).college("공과대학").name("컴퓨터공학과 성능테스트").build());
        Department doubleDept = departmentRepository.save(Department.builder()
                .school(school).college("경영대학").name("경영학과 성능테스트").build());

        Member member = memberRepository.save(Member.builder()
                .nickname("성능테스트학생").oauthProvider(OauthProvider.KAKAO)
                .oauthId("PERF_ALL_001").email(null).build());

        StudentProfile profile = studentProfileRepository.save(StudentProfile.builder()
                .member(member).school(school).department(mainDept).admissionYear(2024).build());

        StudentMajor mainMajor = studentMajorRepository.save(StudentMajor.builder()
                .studentProfile(profile).department(mainDept).majorType(MajorType.MAIN).build());
        StudentMajor doubleMajor = studentMajorRepository.save(StudentMajor.builder()
                .studentProfile(profile).department(doubleDept).majorType(MajorType.DOUBLE).build());

        Division majorBasicDiv = divisionRepository.save(Division.builder()
                .school(school).code("PERF_MAJOR_BASIC").category(DivisionCategory.MAJOR_BASIC).build());
        Division majorRequiredDiv = divisionRepository.save(Division.builder()
                .school(school).code("PERF_MAJOR_REQUIRED").category(DivisionCategory.MAJOR_REQUIRED).build());
        Division majorElectiveDiv = divisionRepository.save(Division.builder()
                .school(school).code("PERF_MAJOR_ELECTIVE").category(DivisionCategory.MAJOR_ELECTIVE).build());
        Division requiredGeDiv = divisionRepository.save(Division.builder()
                .school(school).code("PERF_REQUIRED_GE").category(DivisionCategory.REQUIRED_GE).build());
        Division distGeDiv = divisionRepository.save(Division.builder()
                .school(school).code("PERF_DIST_GE").category(DivisionCategory.DISTRIBUTED_GE).build());
        Division freeGeDiv = divisionRepository.save(Division.builder()
                .school(school).code("PERF_FREE_GE").category(DivisionCategory.FREE_GE).build());
        Division generalElectiveDiv = divisionRepository.save(Division.builder()
                .school(school).code("PERF_GENERAL_ELECTIVE").category(DivisionCategory.GENERAL_ELECTIVE).build());

        List<GeArea> areas = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            areas.add(geAreaRepository.save(
                    GeArea.builder().school(school).code("PERF_AREA_" + i).name("성능영역" + i).build()));
        }

        int seq = 0;

        // 전공기초 5과목 (15학점)
        for (int i = 0; i < 5; i++) {
            saveCourse(profile, school, "PERF-" + (++seq), "전공기초" + i, 3, null, majorBasicDiv, 2022, false, false);
        }
        // 전공필수 7과목 (21학점)
        for (int i = 0; i < 7; i++) {
            saveCourse(profile, school, "PERF-" + (++seq), "전공필수" + i, 3, null, majorRequiredDiv, 2022, false, false);
        }
        // 전공선택 6과목 (18학점)
        for (int i = 0; i < 6; i++) {
            saveCourse(profile, school, "PERF-" + (++seq), "전공선택" + i, 3, null, majorElectiveDiv, 2023, false, false);
        }
        // 필수교과 10과목 (30학점)
        for (int i = 0; i < 10; i++) {
            saveCourse(profile, school, "PERF-" + (++seq), "필수교과" + i, 3, null, requiredGeDiv, 2023, false, false);
        }
        // 배분이수 — 5개 영역 × 2과목 (30학점, 영역 5개 모두 이수)
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 2; j++) {
                saveCourse(profile, school, "PERF-" + (++seq), "배분" + i + "_" + j, 3, areas.get(i), distGeDiv, 2024, false, false);
            }
        }
        // 자유이수 6과목 (18학점)
        for (int i = 0; i < 6; i++) {
            saveCourse(profile, school, "PERF-" + (++seq), "자유이수" + i, 3, null, freeGeDiv, 2024, false, false);
        }
        // 기타(일반선택) 7과목 (21학점)
        for (int i = 0; i < 7; i++) {
            saveCourse(profile, school, "PERF-" + (++seq), "기타" + i, 3, null, generalElectiveDiv, 2024, false, false);
        }

        GraduationAnalysisSummary.GraduationAnalysisSummaryBuilder base = GraduationAnalysisSummary.builder()
                .majorBasicCurrent(15).majorBasicRequired(15)
                .majorRequiredCurrent(21).majorRequiredRequired(21)
                .majorElectiveCurrent(18).majorElectiveRequired(18)
                .requiredPlusElectiveCurrent(39).requiredPlusElectiveRequired(39)
                .requiredGeCurrent(30).requiredGeRequired(30)
                .distributedGeCurrent(30).distributedGeRequired(9)
                .freeGeCurrent(18).freeGeRequired(18)
                .generalElectiveCurrent(21)
                .englishCurrent(4).englishRequired(4)
                .swCertCurrent(6).swCertRequired(6)
                .gpaCurrent(new BigDecimal("3.500")).gpaRequired(new BigDecimal("2.000"))
                .totalCreditCurrent(130).totalCreditRequired(130);

        graduationAnalysisSummaryRepository.save(base.studentMajor(mainMajor).build());
        graduationAnalysisSummaryRepository.save(base.studentMajor(doubleMajor).build());

        Authentication auth = new UsernamePasswordAuthenticationToken(
                member.getId().toString(), null, Collections.emptyList());

        for (int i = 0; i < WARMUP_ROUNDS; i++) {
            mockMvc.perform(get("/api/v1/students/me/graduation").with(authentication(auth)))
                    .andExpect(status().isOk());
        }

        long[] times = new long[MEASURE_ROUNDS];
        for (int i = 0; i < MEASURE_ROUNDS; i++) {
            long start = System.currentTimeMillis();
            mockMvc.perform(get("/api/v1/students/me/graduation").with(authentication(auth)))
                    .andExpect(status().isOk());
            times[i] = System.currentTimeMillis() - start;
        }

        long sum = 0, min = Long.MAX_VALUE, max = Long.MIN_VALUE;
        for (long t : times) {
            sum += t;
            if (t < min) min = t;
            if (t > max) max = t;
        }

        System.out.printf("%n=== getGraduation(ALL) 성능 측정 — 웜업 %d회, 측정 %d회, 과목 %d개 ===%n",
                WARMUP_ROUNDS, MEASURE_ROUNDS, seq);
        for (int i = 0; i < MEASURE_ROUNDS; i++) {
            System.out.printf("  [%2d] %3d ms%n", i + 1, times[i]);
        }
        System.out.printf("  min=%d ms  max=%d ms  avg=%.1f ms%n%n", min, max, (double) sum / MEASURE_ROUNDS);
    }

    private void saveCourse(StudentProfile profile, School school, String code, String name,
            int credit, GeArea geArea, Division division, int year, boolean isEnglish, boolean isSw) {
        Course course = courseRepository.save(Course.builder()
                .school(school).courseCode(code).name(name).credit(credit)
                .recommendedYearLow(1).recommendedYearHigh(4).openedSemester(OpenedSemester.FIRST)
                .isEnglish(isEnglish).isSw(isSw).isActive(true).geArea(geArea).build());
        studentCourseRepository.save(StudentCourse.builder()
                .studentProfile(profile).course(course)
                .rawCourseCode(course.getCourseCode()).rawCourseName(course.getName())
                .credit(credit).takenYear(year).takenSemester(Semester.FIRST)
                .appliedDivision(division)
                .status(CourseStatus.COMPLETED).source(RecordSource.PDF).isRetake(false).build());
    }
}