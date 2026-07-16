package com.growingpots.domain.planner.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    @Autowired PlannerSimulationRepository plannerSimulationRepository;
    @Autowired PlannerTermRepository plannerTermRepository;
    @Autowired PlannerTermVersionRepository plannerTermVersionRepository;
    @Autowired PlannerVersionItemRepository plannerVersionItemRepository;

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

    private String bodyWithTerm(Long plannerTermId, Long... courseIds) {
        StringBuilder sb = new StringBuilder("{\"courseIds\":[");
        for (int i = 0; i < courseIds.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(courseIds[i]);
        }
        sb.append("],\"plannerTermId\":").append(plannerTermId).append("}");
        return sb.toString();
    }

    private PlannerSimulation createSimulation(StudentProfile profile) {
        return plannerSimulationRepository.save(
                PlannerSimulation.builder().studentProfile(profile).name("내 플래너").build());
    }

    private PlannerTerm createTerm(PlannerSimulation simulation, int yearLevel, int semester) {
        return plannerTermRepository.save(
                PlannerTerm.builder().plannerSimulation(simulation).yearLevel(yearLevel).semester(semester).build());
    }

    private PlannerTermVersion createSelectedVersion(PlannerTerm term) {
        return plannerTermVersionRepository.save(PlannerTermVersion.builder()
                .plannerTerm(term).versionNo(1).name("버전1").isSelected(true).versionOrder(1).build());
    }

    private PlannerTermVersion createUnselectedVersion(PlannerTerm term) {
        return plannerTermVersionRepository.save(PlannerTermVersion.builder()
                .plannerTerm(term).versionNo(2).name("버전2").isSelected(false).versionOrder(2).build());
    }

    private void planCourse(PlannerTermVersion version, Course course) {
        plannerVersionItemRepository.save(PlannerVersionItem.builder()
                .plannerTermVersion(version).course(course).plannedDivision(null)
                .credit(course.getCredit()).coursePositionOrder(1).build());
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
                .andExpect(jsonPath("$.data.results[0].name").value("자료구조"))
                .andExpect(jsonPath("$.data.results[0].missingPrerequisites[0].courseId").value(prereq.getId()))
                .andExpect(jsonPath("$.data.results[0].missingPrerequisites[0].name").value("프로그래밍기초"))
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

    // ── plannerTermId 관련 테스트 ──────────────────────────────────────────────

    @Test
    void 이전_학기_플래너_전과목은_선수과목_충족으로_간주() throws Exception {
        // 4-1에 전과목(prereq) 플래너 추가, 4-2 후과목(course) 추가 시 → prereq는 충족
        School school = createSchool("PC013");
        Department dept = createDept(school, "PC013");
        StudentProfile student = createStudent("PC013", school, dept);
        Course course = createCourse(school, "CS301", "운영체제");
        Course prereq = createCourse(school, "CS101", "자료구조");
        createPrerequisite(course, prereq, PrerequisiteType.REQUIRED, null);

        PlannerSimulation sim = createSimulation(student);
        PlannerTerm term41 = createTerm(sim, 4, 1);
        PlannerTerm term42 = createTerm(sim, 4, 2);
        planCourse(createSelectedVersion(term41), prereq);

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithTerm(term42.getId(), course.getId()))
                        .with(authentication(authOf(student.getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.results").isEmpty());
    }

    @Test
    void 같은_학기_플래너_전과목은_선수과목_충족으로_간주_안함() throws Exception {
        // 4-2에 전과목·후과목 모두 → 같은 학기라서 미이수로 판단
        School school = createSchool("PC014");
        Department dept = createDept(school, "PC014");
        StudentProfile student = createStudent("PC014", school, dept);
        Course course = createCourse(school, "CS301", "운영체제");
        Course prereq = createCourse(school, "CS101", "자료구조");
        createPrerequisite(course, prereq, PrerequisiteType.REQUIRED, null);

        PlannerSimulation sim = createSimulation(student);
        PlannerTerm term42 = createTerm(sim, 4, 2);
        planCourse(createSelectedVersion(term42), prereq);

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithTerm(term42.getId(), course.getId()))
                        .with(authentication(authOf(student.getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.results[0].missingPrerequisites[0].courseId").value(prereq.getId()));
    }

    @Test
    void 이후_학기_플래너_전과목은_선수과목_충족으로_간주_안함() throws Exception {
        // 4-2에 전과목, 4-1에 후과목 추가 시 → 전과목이 더 늦으므로 미이수
        School school = createSchool("PC015");
        Department dept = createDept(school, "PC015");
        StudentProfile student = createStudent("PC015", school, dept);
        Course course = createCourse(school, "CS301", "운영체제");
        Course prereq = createCourse(school, "CS101", "자료구조");
        createPrerequisite(course, prereq, PrerequisiteType.REQUIRED, null);

        PlannerSimulation sim = createSimulation(student);
        PlannerTerm term41 = createTerm(sim, 4, 1);
        PlannerTerm term42 = createTerm(sim, 4, 2);
        planCourse(createSelectedVersion(term42), prereq);

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithTerm(term41.getId(), course.getId()))
                        .with(authentication(authOf(student.getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.results[0].missingPrerequisites[0].courseId").value(prereq.getId()));
    }

    @Test
    void plannerTermId_없이_호출하면_플래너_과목_무시() throws Exception {
        // 4-1에 전과목 플래너에 있어도 plannerTermId 없이 호출하면 성적표만 체크 → 미이수
        School school = createSchool("PC016");
        Department dept = createDept(school, "PC016");
        StudentProfile student = createStudent("PC016", school, dept);
        Course course = createCourse(school, "CS301", "운영체제");
        Course prereq = createCourse(school, "CS101", "자료구조");
        createPrerequisite(course, prereq, PrerequisiteType.REQUIRED, null);

        PlannerSimulation sim = createSimulation(student);
        PlannerTerm term41 = createTerm(sim, 4, 1);
        planCourse(createSelectedVersion(term41), prereq);

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(course.getId()))
                        .with(authentication(authOf(student.getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.results[0].missingPrerequisites[0].courseId").value(prereq.getId()));
    }

    @Test
    void 타인의_plannerTermId로_호출시_403_PLAN_003() throws Exception {
        School school = createSchool("PC017");
        Department dept = createDept(school, "PC017");
        StudentProfile owner = createStudent("PC017-owner", school, dept);
        StudentProfile attacker = createStudent("PC017-attacker", school, dept);
        Course course = createCourse(school, "CS301", "운영체제");

        PlannerSimulation ownerSim = createSimulation(owner);
        PlannerTerm ownerTerm = createTerm(ownerSim, 4, 2);

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithTerm(ownerTerm.getId(), course.getId()))
                        .with(authentication(authOf(attacker.getMember().getId()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PLAN_003"));
    }

    @Test
    void 존재하지_않는_plannerTermId로_호출시_404_PLAN_005() throws Exception {
        School school = createSchool("PC018");
        Department dept = createDept(school, "PC018");
        StudentProfile student = createStudent("PC018", school, dept);
        Course course = createCourse(school, "CS301", "운영체제");

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithTerm(999999L, course.getId()))
                        .with(authentication(authOf(student.getMember().getId()))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLAN_005"));
    }

    @Test
    void 선택되지_않은_버전의_플래너_과목은_무시() throws Exception {
        // isSelected=false 버전에만 전과목이 있으면 충족으로 간주하지 않음
        School school = createSchool("PC019");
        Department dept = createDept(school, "PC019");
        StudentProfile student = createStudent("PC019", school, dept);
        Course course = createCourse(school, "CS301", "운영체제");
        Course prereq = createCourse(school, "CS101", "자료구조");
        createPrerequisite(course, prereq, PrerequisiteType.REQUIRED, null);

        PlannerSimulation sim = createSimulation(student);
        PlannerTerm term41 = createTerm(sim, 4, 1);
        PlannerTerm term42 = createTerm(sim, 4, 2);
        planCourse(createUnselectedVersion(term41), prereq);

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithTerm(term42.getId(), course.getId()))
                        .with(authentication(authOf(student.getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.results[0].missingPrerequisites[0].courseId").value(prereq.getId()));
    }
}