package com.growingpots.domain.planner.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.growingpots.domain.transcript.entity.StudentCourse;
import com.growingpots.domain.transcript.entity.enums.CourseStatus;
import com.growingpots.domain.transcript.entity.enums.RecordSource;
import com.growingpots.domain.transcript.repository.StudentCourseRepository;
import com.growingpots.domain.university.entity.Course;
import com.growingpots.domain.university.entity.CoursePrerequisite;
import com.growingpots.domain.university.entity.Department;
import com.growingpots.domain.university.entity.School;
import com.growingpots.domain.university.entity.enums.PrerequisiteType;
import com.growingpots.domain.university.repository.CoursePrerequisiteRepository;
import com.growingpots.domain.university.repository.CourseRepository;
import com.growingpots.domain.university.repository.DepartmentRepository;
import com.growingpots.domain.university.repository.SchoolRepository;
import com.growingpots.domain.user.entity.Member;
import com.growingpots.domain.user.entity.StudentProfile;
import com.growingpots.domain.user.entity.enums.OauthProvider;
import com.growingpots.domain.user.repository.MemberRepository;
import com.growingpots.domain.user.repository.StudentProfileRepository;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class PrerequisiteCheckTest {

    @Autowired MockMvc mockMvc;
    @Autowired MemberRepository memberRepository;
    @Autowired StudentProfileRepository studentProfileRepository;
    @Autowired SchoolRepository schoolRepository;
    @Autowired DepartmentRepository departmentRepository;
    @Autowired CourseRepository courseRepository;
    @Autowired CoursePrerequisiteRepository coursePrerequisiteRepository;
    @Autowired StudentCourseRepository studentCourseRepository;

    private static final String URL = "/api/v1/planner/prerequisite-check";

    private School createSchool(String suffix) {
        return schoolRepository.save(School.builder().name("테스트대학교-" + suffix).build());
    }

    private Department createDept(School school, String suffix) {
        return departmentRepository.save(
                Department.builder().school(school).college("공과대학").name("컴퓨터공학과-" + suffix).build());
    }

    private StudentProfile createStudent(String oauthId, School school, Department dept) {
        Member member = memberRepository.save(Member.builder()
                .nickname("유저-" + oauthId).oauthProvider(OauthProvider.KAKAO)
                .oauthId(oauthId).email(null).build());
        return studentProfileRepository.save(StudentProfile.builder()
                .member(member).school(school).department(dept).admissionYear(2023).build());
    }

    private Course createCourse(School school, String code, String name) {
        return courseRepository.save(Course.builder()
                .school(school).courseCode(code).name(name).credit(3)
                .isEnglish(false).isSw(false).build());
    }

    private CoursePrerequisite createPrerequisite(
            Course course, Course required, PrerequisiteType type, Department dept) {
        return coursePrerequisiteRepository.save(CoursePrerequisite.builder()
                .course(course).requiredCourse(required).prerequisiteType(type).department(dept).build());
    }

    private void markTaken(StudentProfile profile, Course course, CourseStatus status) {
        studentCourseRepository.save(StudentCourse.builder()
                .studentProfile(profile).course(course).rawCourseName(course.getName())
                .credit(course.getCredit()).isRetake(false).status(status).source(RecordSource.MANUAL).build());
    }

    private Authentication authOf(Long memberId) {
        return new UsernamePasswordAuthenticationToken(memberId.toString(), null, Collections.emptyList());
    }

    private String body(Long... courseIds) {
        StringBuilder sb = new StringBuilder("{\"courseIds\":[");
        for (int i = 0; i < courseIds.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(courseIds[i]);
        }
        sb.append("]}");
        return sb.toString();
    }

    @Test
    void REQUIRED_선수과목_미이수시_결과에_포함() throws Exception {
        School school = createSchool("PC001");
        Department dept = createDept(school, "PC001");
        StudentProfile student = createStudent("PC001", school, dept);
        Course course = createCourse(school, "CS101", "자료구조");
        Course prereq = createCourse(school, "CS001", "프로그래밍기초");
        createPrerequisite(course, prereq, PrerequisiteType.REQUIRED, null);

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(course.getId()))
                        .with(authentication(authOf(student.getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PLAN_200_2"))
                .andExpect(jsonPath("$.data.results[0].courseId").value(course.getId()))
                .andExpect(jsonPath("$.data.results[0].courseName").value("자료구조"))
                .andExpect(jsonPath("$.data.results[0].missingPrerequisites[0].courseId").value(prereq.getId()))
                .andExpect(jsonPath("$.data.results[0].missingPrerequisites[0].courseName").value("프로그래밍기초"))
                .andExpect(jsonPath("$.data.results[0].missingPrerequisites[0].type").value("REQUIRED"));
    }

    @Test
    void RECOMMENDED_선수과목_미이수시_결과에_포함() throws Exception {
        School school = createSchool("PC002");
        Department dept = createDept(school, "PC002");
        StudentProfile student = createStudent("PC002", school, dept);
        Course course = createCourse(school, "CS201", "알고리즘");
        Course prereq = createCourse(school, "CS101", "자료구조");
        createPrerequisite(course, prereq, PrerequisiteType.RECOMMENDED, null);

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(course.getId()))
                        .with(authentication(authOf(student.getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PLAN_200_2"))
                .andExpect(jsonPath("$.data.results[0].missingPrerequisites[0].type").value("RECOMMENDED"));
    }

    @Test
    void COMPLETED_이수과목은_선수과목_충족으로_결과에서_제외() throws Exception {
        School school = createSchool("PC003");
        Department dept = createDept(school, "PC003");
        StudentProfile student = createStudent("PC003", school, dept);
        Course course = createCourse(school, "CS101", "자료구조");
        Course prereq = createCourse(school, "CS001", "프로그래밍기초");
        createPrerequisite(course, prereq, PrerequisiteType.REQUIRED, null);
        markTaken(student, prereq, CourseStatus.COMPLETED);

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(course.getId()))
                        .with(authentication(authOf(student.getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PLAN_200_2"))
                .andExpect(jsonPath("$.data.results").isEmpty());
    }

    @Test
    void IN_PROGRESS_수강중_과목도_선수과목_충족으로_결과에서_제외() throws Exception {
        School school = createSchool("PC004");
        Department dept = createDept(school, "PC004");
        StudentProfile student = createStudent("PC004", school, dept);
        Course course = createCourse(school, "CS101", "자료구조");
        Course prereq = createCourse(school, "CS001", "프로그래밍기초");
        createPrerequisite(course, prereq, PrerequisiteType.REQUIRED, null);
        markTaken(student, prereq, CourseStatus.IN_PROGRESS);

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(course.getId()))
                        .with(authentication(authOf(student.getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.results").isEmpty());
    }

    @Test
    void 학과특정_규칙이_공통_규칙보다_우선_적용() throws Exception {
        School school = createSchool("PC005");
        Department dept = createDept(school, "PC005");
        StudentProfile student = createStudent("PC005", school, dept);
        Course course = createCourse(school, "CS301", "운영체제");
        Course prereq = createCourse(school, "CS101", "자료구조");
        // 공통 규칙: REQUIRED, 학과 특정 규칙: RECOMMENDED → RECOMMENDED 우선
        createPrerequisite(course, prereq, PrerequisiteType.REQUIRED, null);
        createPrerequisite(course, prereq, PrerequisiteType.RECOMMENDED, dept);

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(course.getId()))
                        .with(authentication(authOf(student.getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.results[0].missingPrerequisites").isArray())
                .andExpect(jsonPath("$.data.results[0].missingPrerequisites.length()").value(1))
                .andExpect(jsonPath("$.data.results[0].missingPrerequisites[0].type").value("RECOMMENDED"));
    }

    @Test
    void 선수과목_없는_과목은_결과에_포함되지_않음() throws Exception {
        School school = createSchool("PC006");
        Department dept = createDept(school, "PC006");
        StudentProfile student = createStudent("PC006", school, dept);
        Course course = createCourse(school, "GE101", "글쓰기");

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(course.getId()))
                        .with(authentication(authOf(student.getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.results").isEmpty());
    }

    @Test
    void 모든_선수과목_이수시_빈_results_반환() throws Exception {
        School school = createSchool("PC007");
        Department dept = createDept(school, "PC007");
        StudentProfile student = createStudent("PC007", school, dept);
        Course course = createCourse(school, "CS401", "컴파일러");
        Course prereq1 = createCourse(school, "CS101", "자료구조");
        Course prereq2 = createCourse(school, "CS201", "알고리즘");
        createPrerequisite(course, prereq1, PrerequisiteType.REQUIRED, null);
        createPrerequisite(course, prereq2, PrerequisiteType.REQUIRED, null);
        markTaken(student, prereq1, CourseStatus.COMPLETED);
        markTaken(student, prereq2, CourseStatus.COMPLETED);

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(course.getId()))
                        .with(authentication(authOf(student.getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.results").isEmpty());
    }

    @Test
    void 필수_권장_혼재시_missingPrerequisites_2건_반환() throws Exception {
        School school = createSchool("PC009a");
        Department dept = createDept(school, "PC009a");
        StudentProfile student = createStudent("PC009a", school, dept);
        Course course = createCourse(school, "CS401", "고급공학수학");
        Course prereq1 = createCourse(school, "CS101", "공학수학");
        Course prereq2 = createCourse(school, "CS201", "선형대수");
        createPrerequisite(course, prereq1, PrerequisiteType.REQUIRED, null);
        createPrerequisite(course, prereq2, PrerequisiteType.RECOMMENDED, null);

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(course.getId()))
                        .with(authentication(authOf(student.getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.results[0].missingPrerequisites.length()").value(2));
    }

    @Test
    void 타학과_전용_규칙은_내_학과에_미적용() throws Exception {
        School school = createSchool("PC009b");
        Department myDept = createDept(school, "PC009b-mine");
        Department otherDept = createDept(school, "PC009b-other");
        StudentProfile student = createStudent("PC009b", school, myDept);
        Course course = createCourse(school, "CS501", "네트워크");
        Course prereq = createCourse(school, "CS001", "프로그래밍기초");
        // 타 학과 전용 규칙만 존재 → 내 학과에는 적용되지 않아야 함
        createPrerequisite(course, prereq, PrerequisiteType.REQUIRED, otherDept);

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(course.getId()))
                        .with(authentication(authOf(student.getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.results").isEmpty());
    }

    @Test
    void courseIds_빈배열_400_CMN_002() throws Exception {
        School school = createSchool("PC008");
        Department dept = createDept(school, "PC008");
        StudentProfile student = createStudent("PC008", school, dept);

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseIds\":[]}")
                        .with(authentication(authOf(student.getMember().getId()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN_002"));
    }

    @Test
    void 존재하지_않는_courseId_요청시_404_PLAN_001() throws Exception {
        School school = createSchool("PC011");
        Department dept = createDept(school, "PC011");
        StudentProfile student = createStudent("PC011", school, dept);

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(999999L))
                        .with(authentication(authOf(student.getMember().getId()))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLAN_001"));
    }

    @Test
    void 타학교_courseId_요청시_404_PLAN_001() throws Exception {
        School mySchool = createSchool("PC012-mine");
        School otherSchool = createSchool("PC012-other");
        Department myDept = createDept(mySchool, "PC012");
        StudentProfile student = createStudent("PC012", mySchool, myDept);
        Course otherCourse = createCourse(otherSchool, "CS999", "타교과목");

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(otherCourse.getId()))
                        .with(authentication(authOf(student.getMember().getId()))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLAN_001"));
    }

    @Test
    void 미인증_요청시_401_CMN_005() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(1L)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("CMN_005"));
    }

    @Test
    void 학적미완료_회원_404_USER_003() throws Exception {
        Member member = memberRepository.save(Member.builder()
                .nickname("미완료유저").oauthProvider(OauthProvider.KAKAO)
                .oauthId("PC010-no-profile").email(null).build());

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(1L))
                        .with(authentication(authOf(member.getId()))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_003"));
    }
}