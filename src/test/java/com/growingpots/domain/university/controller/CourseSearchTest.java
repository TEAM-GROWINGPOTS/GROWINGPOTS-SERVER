package com.growingpots.domain.university.controller;

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
import com.growingpots.domain.transcript.entity.StudentCourse;
import com.growingpots.domain.transcript.entity.enums.CourseStatus;
import com.growingpots.domain.transcript.entity.enums.RecordSource;
import com.growingpots.domain.transcript.entity.enums.Semester;
import com.growingpots.domain.transcript.repository.StudentCourseRepository;
import com.growingpots.domain.university.entity.Course;
import com.growingpots.domain.university.entity.CrossMajorRecognizedCourse;
import com.growingpots.domain.university.entity.Department;
import com.growingpots.domain.university.entity.Division;
import com.growingpots.domain.university.entity.School;
import com.growingpots.domain.university.entity.enums.DivisionCategory;
import com.growingpots.domain.university.entity.enums.OpenedSemester;
import com.growingpots.domain.university.repository.CourseRepository;
import com.growingpots.domain.university.repository.CrossMajorRecognizedCourseRepository;
import com.growingpots.domain.university.repository.DepartmentRepository;
import com.growingpots.domain.university.repository.DivisionRepository;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class CourseSearchTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private StudentProfileRepository studentProfileRepository;

    @Autowired
    private SchoolRepository schoolRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private DivisionRepository divisionRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CrossMajorRecognizedCourseRepository crossMajorRecognizedCourseRepository;

    @Autowired
    private StudentCourseRepository studentCourseRepository;

    @Autowired
    private PlannerSimulationRepository plannerSimulationRepository;

    @Autowired
    private PlannerTermRepository plannerTermRepository;

    @Autowired
    private PlannerTermVersionRepository plannerTermVersionRepository;

    @Autowired
    private PlannerVersionItemRepository plannerVersionItemRepository;

    private StudentProfile onboardedStudent(String oauthId, Department department) {
        Member member = memberRepository.save(Member.builder()
                .nickname("테스트유저")
                .oauthProvider(OauthProvider.KAKAO)
                .oauthId(oauthId)
                .email(null)
                .build());
        return studentProfileRepository.save(StudentProfile.builder()
                .member(member)
                .school(department.getSchool())
                .department(department)
                .admissionYear(2023)
                .build());
    }

    private Authentication authenticationOf(Long memberId) {
        return new UsernamePasswordAuthenticationToken(memberId.toString(), null, Collections.emptyList());
    }

    @Test
    void 필터_없이_조회하면_학교_소속_전체_과목이_반환된다() throws Exception {
        School school = schoolRepository.save(School.builder().name("경희대학교-9101").build());
        Department cs = departmentRepository.save(Department.builder()
                .school(school).college("공과대학").name("컴퓨터공학과").build());
        Division majorRequired = divisionRepository.save(Division.builder()
                .school(school).code("04").category(DivisionCategory.MAJOR_REQUIRED).build());
        courseRepository.save(Course.builder()
                .school(school).courseCode("CS101").name("컴퓨터구조론").credit(3)
                .offeringDepartment(cs).defaultDivision(majorRequired)
                .recommendedYearLow(2).recommendedYearHigh(2).openedSemester(OpenedSemester.FIRST)
                .isEnglish(false).isSw(false).isActive(true).build());
        StudentProfile studentProfile = onboardedStudent("9101", cs);

        mockMvc.perform(get("/api/v1/courses")
                        .with(authentication(authenticationOf(studentProfile.getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("UNIV_200_3"))
                .andExpect(jsonPath("$.data.courses.length()").value(1))
                .andExpect(jsonPath("$.data.courses[0].courseCode").value("CS101"))
                .andExpect(jsonPath("$.data.courses[0].defaultDivisionName").value("전공필수"))
                .andExpect(jsonPath("$.data.page.totalElements").value(1));
    }

    @Test
    void isActive가_false인_과목은_검색_결과에서_빠진다() throws Exception {
        School school = schoolRepository.save(School.builder().name("경희대학교-9111").build());
        Department cs = departmentRepository.save(Department.builder()
                .school(school).college("공과대학").name("컴퓨터공학과").build());
        courseRepository.save(Course.builder()
                .school(school).courseCode("CS101").name("현재교육과정과목").credit(3)
                .offeringDepartment(cs).recommendedYearLow(1).recommendedYearHigh(1)
                .openedSemester(OpenedSemester.FIRST).isEnglish(false).isSw(false).isActive(true).build());
        courseRepository.save(Course.builder()
                .school(school).courseCode("CS099").name("폐지된옛날과목").credit(3)
                .offeringDepartment(cs).recommendedYearLow(1).recommendedYearHigh(1)
                .openedSemester(OpenedSemester.FIRST).isEnglish(false).isSw(false).isActive(false).build());
        StudentProfile studentProfile = onboardedStudent("9111", cs);

        mockMvc.perform(get("/api/v1/courses")
                        .with(authentication(authenticationOf(studentProfile.getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.courses.length()").value(1))
                .andExpect(jsonPath("$.data.courses[0].courseCode").value("CS101"));
    }

    @Test
    void keyword로_과목명과_학수번호_부분일치_검색이_된다() throws Exception {
        School school = schoolRepository.save(School.builder().name("경희대학교-9102").build());
        Department cs = departmentRepository.save(Department.builder()
                .school(school).college("공과대학").name("컴퓨터공학과").build());
        courseRepository.save(Course.builder()
                .school(school).courseCode("CS102").name("논리회로실습").credit(3)
                .offeringDepartment(cs).recommendedYearLow(1).recommendedYearHigh(1)
                .openedSemester(OpenedSemester.SECOND).isEnglish(false).isSw(false).isActive(true).build());
        courseRepository.save(Course.builder()
                .school(school).courseCode("LOG201").name("논리학과사고").credit(4)
                .offeringDepartment(cs).recommendedYearLow(3).recommendedYearHigh(3)
                .openedSemester(OpenedSemester.FIRST).isEnglish(false).isSw(false).isActive(true).build());
        courseRepository.save(Course.builder()
                .school(school).courseCode("CS101").name("컴퓨터구조론").credit(3)
                .offeringDepartment(cs).recommendedYearLow(2).recommendedYearHigh(2)
                .openedSemester(OpenedSemester.FIRST).isEnglish(false).isSw(false).isActive(true).build());
        StudentProfile studentProfile = onboardedStudent("9102", cs);

        mockMvc.perform(get("/api/v1/courses")
                        .param("keyword", "논리")
                        .with(authentication(authenticationOf(studentProfile.getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.courses.length()").value(2));

        mockMvc.perform(get("/api/v1/courses")
                        .param("keyword", "CS10")
                        .with(authentication(authenticationOf(studentProfile.getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.courses.length()").value(2));
    }

    @Test
    void 이수영역_다중선택은_OR로_학년_필터와는_AND로_결합된다() throws Exception {
        School school = schoolRepository.save(School.builder().name("경희대학교-9103").build());
        Department cs = departmentRepository.save(Department.builder()
                .school(school).college("공과대학").name("컴퓨터공학과").build());
        Division majorRequired = divisionRepository.save(Division.builder()
                .school(school).code("04").category(DivisionCategory.MAJOR_REQUIRED).build());
        Division majorElective = divisionRepository.save(Division.builder()
                .school(school).code("05").category(DivisionCategory.MAJOR_ELECTIVE).build());
        Division geRequired = divisionRepository.save(Division.builder()
                .school(school).code("01").category(DivisionCategory.REQUIRED_GE).build());

        // 전공필수, 2학년 -> 매칭
        courseRepository.save(Course.builder()
                .school(school).courseCode("CS101").name("컴퓨터구조론").credit(3)
                .offeringDepartment(cs).defaultDivision(majorRequired)
                .recommendedYearLow(2).recommendedYearHigh(2).openedSemester(OpenedSemester.FIRST)
                .isEnglish(false).isSw(false).isActive(true).build());
        // 전공선택, 1학년 -> year=[2] 조건에 안 걸림
        courseRepository.save(Course.builder()
                .school(school).courseCode("CS102").name("논리회로실습").credit(3)
                .offeringDepartment(cs).defaultDivision(majorElective)
                .recommendedYearLow(1).recommendedYearHigh(1).openedSemester(OpenedSemester.SECOND)
                .isEnglish(false).isSw(false).isActive(true).build());
        // 전공선택, 1~2학년 범위 -> 2학년 포함이라 매칭
        courseRepository.save(Course.builder()
                .school(school).courseCode("DES101").name("설계입문").credit(2)
                .offeringDepartment(cs).defaultDivision(majorElective)
                .recommendedYearLow(1).recommendedYearHigh(2).openedSemester(OpenedSemester.FIRST)
                .isEnglish(false).isSw(false).isActive(true).build());
        // 이수영역 자체가 필터에 없는 필수교과, 2학년 -> 이수영역 조건에 안 걸림
        courseRepository.save(Course.builder()
                .school(school).courseCode("GE101").name("대학영어").credit(2)
                .defaultDivision(geRequired)
                .recommendedYearLow(2).recommendedYearHigh(2).openedSemester(OpenedSemester.BOTH)
                .isEnglish(true).isSw(false).isActive(true).build());
        StudentProfile studentProfile = onboardedStudent("9103", cs);

        mockMvc.perform(get("/api/v1/courses")
                        .param("divisionCategory", "MAJOR_REQUIRED", "MAJOR_ELECTIVE")
                        .param("year", "2")
                        .with(authentication(authenticationOf(studentProfile.getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.courses.length()").value(2))
                .andExpect(jsonPath("$.data.courses[*].courseCode")
                        .value(org.hamcrest.Matchers.containsInAnyOrder("CS101", "DES101")));
    }

    @Test
    void credits에_4가_포함되면_4학점_이상으로_처리된다() throws Exception {
        School school = schoolRepository.save(School.builder().name("경희대학교-9104").build());
        Department cs = departmentRepository.save(Department.builder()
                .school(school).college("공과대학").name("컴퓨터공학과").build());
        courseRepository.save(Course.builder()
                .school(school).courseCode("A1").name("과목A").credit(3)
                .offeringDepartment(cs).recommendedYearLow(1).recommendedYearHigh(1)
                .openedSemester(OpenedSemester.FIRST).isEnglish(false).isSw(false).isActive(true).build());
        courseRepository.save(Course.builder()
                .school(school).courseCode("A2").name("과목B").credit(4)
                .offeringDepartment(cs).recommendedYearLow(1).recommendedYearHigh(1)
                .openedSemester(OpenedSemester.FIRST).isEnglish(false).isSw(false).isActive(true).build());
        courseRepository.save(Course.builder()
                .school(school).courseCode("A3").name("과목C").credit(5)
                .offeringDepartment(cs).recommendedYearLow(1).recommendedYearHigh(1)
                .openedSemester(OpenedSemester.FIRST).isEnglish(false).isSw(false).isActive(true).build());
        StudentProfile studentProfile = onboardedStudent("9104", cs);

        mockMvc.perform(get("/api/v1/courses")
                        .param("credits", "4")
                        .with(authentication(authenticationOf(studentProfile.getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.courses.length()").value(2))
                .andExpect(jsonPath("$.data.courses[*].courseCode")
                        .value(org.hamcrest.Matchers.containsInAnyOrder("A2", "A3")));
    }

    // "1학기+2학기" 동시 선택은 "전체학기(BOTH)" 단독 선택과 정확히 같은 결과셋이어야 한다(#219, 기획
    // 요구사항). BOTH 선택은 학기 필터를 아예 안 건 것과 동일하게 전체가 나와야 하고, FIRST/SECOND
    // 단독 선택엔 그 학기 전용 과목뿐 아니라 BOTH(매학기 개설) 과목도 같이 포함돼야 한다.
    @Test
    void 학기_필터에서_1학기와_2학기를_동시선택하면_전체학기_선택과_결과가_같다() throws Exception {
        School school = schoolRepository.save(School.builder().name("경희대학교-9114").build());
        Department cs = departmentRepository.save(Department.builder()
                .school(school).college("공과대학").name("컴퓨터공학과").build());
        courseRepository.save(Course.builder()
                .school(school).courseCode("F1").name("1학기전용과목").credit(3)
                .offeringDepartment(cs).recommendedYearLow(1).recommendedYearHigh(1)
                .openedSemester(OpenedSemester.FIRST).isEnglish(false).isSw(false).isActive(true).build());
        courseRepository.save(Course.builder()
                .school(school).courseCode("S1").name("2학기전용과목").credit(3)
                .offeringDepartment(cs).recommendedYearLow(1).recommendedYearHigh(1)
                .openedSemester(OpenedSemester.SECOND).isEnglish(false).isSw(false).isActive(true).build());
        courseRepository.save(Course.builder()
                .school(school).courseCode("B1").name("매학기개설과목").credit(3)
                .offeringDepartment(cs).recommendedYearLow(1).recommendedYearHigh(1)
                .openedSemester(OpenedSemester.BOTH).isEnglish(false).isSw(false).isActive(true).build());
        StudentProfile studentProfile = onboardedStudent("9114", cs);
        Authentication auth = authenticationOf(studentProfile.getMember().getId());

        // 전체학기(BOTH) 단독 선택 -> 전체 3개 다 나와야 함(학기 필터 안 건 것과 동일).
        mockMvc.perform(get("/api/v1/courses")
                        .param("semester", "BOTH")
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.courses.length()").value(3));

        // 1학기 단독 선택 -> 1학기전용 + 매학기개설, 2학기전용은 제외.
        mockMvc.perform(get("/api/v1/courses")
                        .param("semester", "FIRST")
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.courses.length()").value(2))
                .andExpect(jsonPath("$.data.courses[*].courseCode")
                        .value(org.hamcrest.Matchers.containsInAnyOrder("F1", "B1")));

        // 2학기 단독 선택 -> 2학기전용 + 매학기개설, 1학기전용은 제외.
        mockMvc.perform(get("/api/v1/courses")
                        .param("semester", "SECOND")
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.courses.length()").value(2))
                .andExpect(jsonPath("$.data.courses[*].courseCode")
                        .value(org.hamcrest.Matchers.containsInAnyOrder("S1", "B1")));

        // 1학기+2학기 동시 선택 -> 전체학기(BOTH) 단독 선택과 정확히 같은 결과(3개 전부).
        mockMvc.perform(get("/api/v1/courses")
                        .param("semester", "FIRST", "SECOND")
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.courses.length()").value(3))
                .andExpect(jsonPath("$.data.courses[*].courseCode")
                        .value(org.hamcrest.Matchers.containsInAnyOrder("F1", "S1", "B1")));
    }

    @Test
    void 권장학년이_없는_과목은_학년_필터를_걸어도_제외되지_않는다() throws Exception {
        School school = schoolRepository.save(School.builder().name("경희대학교-9109").build());
        Department cs = departmentRepository.save(Department.builder()
                .school(school).college("공과대학").name("컴퓨터공학과").build());
        courseRepository.save(Course.builder()
                .school(school).courseCode("B1").name("권장학년있음").credit(3)
                .offeringDepartment(cs).recommendedYearLow(2).recommendedYearHigh(2)
                .openedSemester(OpenedSemester.FIRST).isEnglish(false).isSw(false).isActive(true).build());
        courseRepository.save(Course.builder()
                .school(school).courseCode("B2").name("권장학년없음").credit(3)
                .offeringDepartment(cs)
                .openedSemester(OpenedSemester.FIRST).isEnglish(false).isSw(false).isActive(true).build());
        StudentProfile studentProfile = onboardedStudent("9109", cs);

        mockMvc.perform(get("/api/v1/courses")
                        .param("year", "2")
                        .with(authentication(authenticationOf(studentProfile.getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.courses.length()").value(2))
                .andExpect(jsonPath("$.data.courses[*].courseCode")
                        .value(org.hamcrest.Matchers.containsInAnyOrder("B1", "B2")));
    }

    @Test
    void size가_0이하이거나_100을_초과하면_400_CMN_002를_반환한다() throws Exception {
        School school = schoolRepository.save(School.builder().name("경희대학교-9110").build());
        Department cs = departmentRepository.save(Department.builder()
                .school(school).college("공과대학").name("컴퓨터공학과").build());
        StudentProfile studentProfile = onboardedStudent("9110", cs);

        mockMvc.perform(get("/api/v1/courses")
                        .param("size", "0")
                        .with(authentication(authenticationOf(studentProfile.getMember().getId()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN_002"));

        mockMvc.perform(get("/api/v1/courses")
                        .param("size", "101")
                        .with(authentication(authenticationOf(studentProfile.getMember().getId()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN_002"));

        mockMvc.perform(get("/api/v1/courses")
                        .param("page", "-1")
                        .with(authentication(authenticationOf(studentProfile.getMember().getId()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN_002"));
    }

    @Test
    void CROSS_MAJOR로_필터링하면_학생_학과_기준_인정_과목만_나오고_인정_이수구분으로_표시된다() throws Exception {
        School school = schoolRepository.save(School.builder().name("경희대학교-9105").build());
        Department chem = departmentRepository.save(Department.builder()
                .school(school).college("공과대학").name("화학공학과").build());
        Department newMat = departmentRepository.save(Department.builder()
                .school(school).college("공과대학").name("신소재공학과").build());
        Division chemMajorRequired = divisionRepository.save(Division.builder()
                .school(school).code("04").category(DivisionCategory.MAJOR_REQUIRED).build());
        Division recognizedAsElective = divisionRepository.save(Division.builder()
                .school(school).code("05").category(DivisionCategory.MAJOR_ELECTIVE).build());

        Course crossMajorCourse = courseRepository.save(Course.builder()
                .school(school).courseCode("MAT201").name("신소재공학개론").credit(3)
                .offeringDepartment(newMat).defaultDivision(chemMajorRequired)
                .recommendedYearLow(2).recommendedYearHigh(2).openedSemester(OpenedSemester.FIRST)
                .isEnglish(false).isSw(false).isActive(true).build());
        courseRepository.save(Course.builder()
                .school(school).courseCode("CHEM101").name("화학공학기초").credit(3)
                .offeringDepartment(chem).defaultDivision(chemMajorRequired)
                .recommendedYearLow(1).recommendedYearHigh(1).openedSemester(OpenedSemester.FIRST)
                .isEnglish(false).isSw(false).isActive(true).build());

        // 화학공학과가 신소재공학과 과목을 "전공선택"으로 인정
        crossMajorRecognizedCourseRepository.save(CrossMajorRecognizedCourse.builder()
                .targetDepartment(chem).course(crossMajorCourse).recognizedDivision(recognizedAsElective).build());

        StudentProfile chemStudent = onboardedStudent("9105", chem);

        mockMvc.perform(get("/api/v1/courses")
                        .param("divisionCategory", "CROSS_MAJOR")
                        .with(authentication(authenticationOf(chemStudent.getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.courses.length()").value(1))
                .andExpect(jsonPath("$.data.courses[0].courseCode").value("MAT201"))
                .andExpect(jsonPath("$.data.courses[0].defaultDivisionName").value("전공선택"));
    }

    @Test
    void alreadyCompleted는_학생이_이수한_과목만_true다() throws Exception {
        School school = schoolRepository.save(School.builder().name("경희대학교-9106").build());
        Department cs = departmentRepository.save(Department.builder()
                .school(school).college("공과대학").name("컴퓨터공학과").build());
        Course completed = courseRepository.save(Course.builder()
                .school(school).courseCode("CS101").name("컴퓨터구조론").credit(3)
                .offeringDepartment(cs).recommendedYearLow(2).recommendedYearHigh(2)
                .openedSemester(OpenedSemester.FIRST).isEnglish(false).isSw(false).isActive(true).build());
        courseRepository.save(Course.builder()
                .school(school).courseCode("CS102").name("논리회로실습").credit(3)
                .offeringDepartment(cs).recommendedYearLow(1).recommendedYearHigh(1)
                .openedSemester(OpenedSemester.SECOND).isEnglish(false).isSw(false).isActive(true).build());
        StudentProfile studentProfile = onboardedStudent("9106", cs);

        studentCourseRepository.save(StudentCourse.builder()
                .studentProfile(studentProfile)
                .course(completed)
                .rawCourseCode("CS101")
                .rawCourseName("컴퓨터구조론")
                .credit(3)
                .takenYear(2024)
                .takenSemester(Semester.FIRST)
                .status(CourseStatus.COMPLETED)
                .source(RecordSource.PDF)
                .isRetake(false)
                .build());

        mockMvc.perform(get("/api/v1/courses")
                        .with(authentication(authenticationOf(studentProfile.getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.courses[?(@.courseCode == 'CS101')].alreadyCompleted").value(true))
                .andExpect(jsonPath("$.data.courses[?(@.courseCode == 'CS102')].alreadyCompleted").value(false));
    }

    @Test
    void inPlanner는_현재_선택된_플래너_버전에_담긴_과목만_true다() throws Exception {
        School school = schoolRepository.save(School.builder().name("경희대학교-9112").build());
        Department cs = departmentRepository.save(Department.builder()
                .school(school).college("공과대학").name("컴퓨터공학과").build());
        Course inPlannerCourse = courseRepository.save(Course.builder()
                .school(school).courseCode("CS201").name("자료구조").credit(3)
                .offeringDepartment(cs).recommendedYearLow(2).recommendedYearHigh(2)
                .openedSemester(OpenedSemester.FIRST).isEnglish(false).isSw(false).isActive(true).build());
        courseRepository.save(Course.builder()
                .school(school).courseCode("CS202").name("운영체제").credit(3)
                .offeringDepartment(cs).recommendedYearLow(2).recommendedYearHigh(2)
                .openedSemester(OpenedSemester.SECOND).isEnglish(false).isSw(false).isActive(true).build());
        StudentProfile studentProfile = onboardedStudent("9112", cs);

        PlannerSimulation simulation = plannerSimulationRepository.save(PlannerSimulation.builder()
                .studentProfile(studentProfile).name("내 플래너").build());
        PlannerTerm term = plannerTermRepository.save(PlannerTerm.builder()
                .plannerSimulation(simulation).yearLevel(2).semester(1).build());
        PlannerTermVersion version = plannerTermVersionRepository.save(PlannerTermVersion.builder()
                .plannerTerm(term).versionNo(1).name("폴더 1").isSelected(true).versionOrder(0).build());
        plannerVersionItemRepository.save(PlannerVersionItem.builder()
                .plannerTermVersion(version).course(inPlannerCourse).plannedDivision(null)
                .credit(3).coursePositionOrder(0).build());

        mockMvc.perform(get("/api/v1/courses")
                        .with(authentication(authenticationOf(studentProfile.getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.courses[?(@.courseCode == 'CS201')].inPlanner").value(true))
                .andExpect(jsonPath("$.data.courses[?(@.courseCode == 'CS202')].inPlanner").value(false));
    }

    @Test
    void size보다_과목이_많으면_hasNext가_true다() throws Exception {
        School school = schoolRepository.save(School.builder().name("경희대학교-9107").build());
        Department cs = departmentRepository.save(Department.builder()
                .school(school).college("공과대학").name("컴퓨터공학과").build());
        for (int i = 0; i < 3; i++) {
            courseRepository.save(Course.builder()
                    .school(school).courseCode("CS10" + i).name("과목" + i).credit(3)
                    .offeringDepartment(cs).recommendedYearLow(1).recommendedYearHigh(1)
                    .openedSemester(OpenedSemester.FIRST).isEnglish(false).isSw(false).isActive(true).build());
        }
        StudentProfile studentProfile = onboardedStudent("9107", cs);

        mockMvc.perform(get("/api/v1/courses")
                        .param("size", "2")
                        .with(authentication(authenticationOf(studentProfile.getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.courses.length()").value(2))
                .andExpect(jsonPath("$.data.page.hasNext").value(true))
                .andExpect(jsonPath("$.data.page.totalElements").value(3));
    }

    @Test
    void 잘못된_divisionCategory값이면_400_CMN_002를_반환한다() throws Exception {
        School school = schoolRepository.save(School.builder().name("경희대학교-9108").build());
        Department cs = departmentRepository.save(Department.builder()
                .school(school).college("공과대학").name("컴퓨터공학과").build());
        StudentProfile studentProfile = onboardedStudent("9108", cs);

        mockMvc.perform(get("/api/v1/courses")
                        .param("divisionCategory", "INVALID_VALUE")
                        .with(authentication(authenticationOf(studentProfile.getMember().getId()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN_002"));
    }

    @Test
    void otherRequired_다중선택은_SW인증이거나_영어강의인_과목을_OR로_반환한다() throws Exception {
        School school = schoolRepository.save(School.builder().name("경희대학교-9113").build());
        Department cs = departmentRepository.save(Department.builder()
                .school(school).college("공과대학").name("컴퓨터공학과").build());
        courseRepository.save(Course.builder()
                .school(school).courseCode("SW1").name("SW인증과목").credit(3)
                .offeringDepartment(cs).recommendedYearLow(1).recommendedYearHigh(1)
                .openedSemester(OpenedSemester.FIRST).isEnglish(false).isSw(true).isActive(true).build());
        courseRepository.save(Course.builder()
                .school(school).courseCode("ENG1").name("영어강의과목").credit(3)
                .offeringDepartment(cs).recommendedYearLow(1).recommendedYearHigh(1)
                .openedSemester(OpenedSemester.FIRST).isEnglish(true).isSw(false).isActive(true).build());
        courseRepository.save(Course.builder()
                .school(school).courseCode("BOTH1").name("SW인증영어강의과목").credit(3)
                .offeringDepartment(cs).recommendedYearLow(1).recommendedYearHigh(1)
                .openedSemester(OpenedSemester.FIRST).isEnglish(true).isSw(true).isActive(true).build());
        courseRepository.save(Course.builder()
                .school(school).courseCode("NONE1").name("일반과목").credit(3)
                .offeringDepartment(cs).recommendedYearLow(1).recommendedYearHigh(1)
                .openedSemester(OpenedSemester.FIRST).isEnglish(false).isSw(false).isActive(true).build());
        StudentProfile studentProfile = onboardedStudent("9113", cs);

        mockMvc.perform(get("/api/v1/courses")
                        .param("otherRequired", "SW")
                        .with(authentication(authenticationOf(studentProfile.getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.courses[*].courseCode")
                        .value(org.hamcrest.Matchers.containsInAnyOrder("SW1", "BOTH1")));

        mockMvc.perform(get("/api/v1/courses")
                        .param("otherRequired", "ENGLISH")
                        .with(authentication(authenticationOf(studentProfile.getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.courses[*].courseCode")
                        .value(org.hamcrest.Matchers.containsInAnyOrder("ENG1", "BOTH1")));

        // 둘 다 선택하면 AND가 아니라 OR로 결합된다 (SW인증 이거나 영어강의인 과목)
        mockMvc.perform(get("/api/v1/courses")
                        .param("otherRequired", "SW", "ENGLISH")
                        .with(authentication(authenticationOf(studentProfile.getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.courses[*].courseCode")
                        .value(org.hamcrest.Matchers.containsInAnyOrder("SW1", "ENG1", "BOTH1")));

        mockMvc.perform(get("/api/v1/courses")
                        .with(authentication(authenticationOf(studentProfile.getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.courses.length()").value(4));
    }

    @Test
    void 인증_헤더가_없으면_401_CMN_005를_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/courses"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("CMN_005"));
    }
}
