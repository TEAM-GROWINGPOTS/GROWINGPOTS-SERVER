package com.growingpots.domain.planner.controller;

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
class PlannerGetTest {

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
    private GeAreaRepository geAreaRepository;

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

    private StudentProfile onboardedStudent(String oauthId, Department department, int admissionYear) {
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
                .admissionYear(admissionYear)
                .build());
    }

    private Authentication authenticationOf(Long memberId) {
        return new UsernamePasswordAuthenticationToken(memberId.toString(), null, Collections.emptyList());
    }

    @Test
    void 이수완료와_이수중_학기가_학년학기로_묶여_상태와_총학점과_함께_반환된다() throws Exception {
        School school = schoolRepository.save(School.builder().name("경희대학교-7701").build());
        Department media = departmentRepository.save(Department.builder()
                .school(school).college("문화대학").name("미디어학과").build());
        Division majorRequired = divisionRepository.save(Division.builder()
                .school(school).code("04").category(DivisionCategory.MAJOR_REQUIRED).build());
        Course course = courseRepository.save(Course.builder()
                .school(school).courseCode("MED101").name("미디어와사회").credit(3)
                .offeringDepartment(media).recommendedYearLow(1).recommendedYearHigh(1)
                .openedSemester(OpenedSemester.FIRST).isEnglish(false).isSw(false).build());
        StudentProfile profile = onboardedStudent("7701", media, 2023);

        studentCourseRepository.save(StudentCourse.builder()
                .studentProfile(profile).course(course).appliedDivision(majorRequired)
                .rawCourseCode("MED101").rawCourseName("미디어와사회").credit(3)
                .takenYear(2023).takenSemester(Semester.FIRST)
                .status(CourseStatus.COMPLETED).source(RecordSource.PDF).isRetake(false).build());
        studentCourseRepository.save(StudentCourse.builder()
                .studentProfile(profile).course(null).appliedDivision(majorRequired)
                .rawCourseCode(null).rawCourseName("연극문헌과연기").credit(3)
                .takenYear(2023).takenSemester(Semester.SECOND)
                .status(CourseStatus.IN_PROGRESS).source(RecordSource.PDF).isRetake(false).build());

        mockMvc.perform(get("/api/v1/planner")
                        .with(authentication(authenticationOf(profile.getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PLAN_200"))
                .andExpect(jsonPath("$.data.completedTerms.length()").value(2))
                .andExpect(jsonPath("$.data.completedTerms[0].yearLevel").value(1))
                .andExpect(jsonPath("$.data.completedTerms[0].semester").value(1))
                .andExpect(jsonPath("$.data.completedTerms[0].name").value("1학년 1학기"))
                .andExpect(jsonPath("$.data.completedTerms[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.completedTerms[0].totalCredit").value(3))
                .andExpect(jsonPath("$.data.completedTerms[0].courses[0].departmentName").value("미디어학과"))
                .andExpect(jsonPath("$.data.completedTerms[0].courses[0].divisionName").value("전공필수"))
                .andExpect(jsonPath("$.data.completedTerms[1].semester").value(2))
                .andExpect(jsonPath("$.data.completedTerms[1].status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.completedTerms[1].courses[0].courseId").doesNotExist())
                .andExpect(jsonPath("$.data.completedTerms[1].courses[0].name").value("연극문헌과연기"));
    }

    // course가 매칭돼도 course.getName()이 아니라 rawCourseName을 그대로 보여줘야 한다.
    // rawCourseName은 PDF 임포트 시 COURSE 이름으로 정리되거나 사용자가 편집한 값이라, 여기서
    // course.getName()으로 다시 덮어쓰면 사용자의 편집이 무시된다.
    @Test
    void 완료된_과목은_course가_매칭돼도_rawCourseName을_그대로_보여준다() throws Exception {
        School school = schoolRepository.save(School.builder().name("경희대학교-7708").build());
        Department media = departmentRepository.save(Department.builder()
                .school(school).college("문화대학").name("미디어학과").build());
        Division majorRequired = divisionRepository.save(Division.builder()
                .school(school).code("04").category(DivisionCategory.MAJOR_REQUIRED).build());
        Course course = courseRepository.save(Course.builder()
                .school(school).courseCode("MED201").name("미디어와사회").credit(3)
                .offeringDepartment(media).recommendedYearLow(1).recommendedYearHigh(1)
                .openedSemester(OpenedSemester.FIRST).isEnglish(false).isSw(false).build());
        StudentProfile profile = onboardedStudent("7708", media, 2023);

        studentCourseRepository.save(StudentCourse.builder()
                .studentProfile(profile).course(course).appliedDivision(majorRequired)
                .rawCourseCode("MED201").rawCourseName("미디어와사회(편집됨)").credit(3)
                .takenYear(2023).takenSemester(Semester.FIRST)
                .status(CourseStatus.COMPLETED).source(RecordSource.PDF).isRetake(false).build());

        mockMvc.perform(get("/api/v1/planner")
                        .with(authentication(authenticationOf(profile.getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completedTerms[0].courses[0].name").value("미디어와사회(편집됨)"));
    }

    @Test
    void 검수가_끝나지_않아_이수구분이_없는_과목은_completedTerms에서_빠진다() throws Exception {
        School school = schoolRepository.save(School.builder().name("경희대학교-7702").build());
        Department cs = departmentRepository.save(Department.builder()
                .school(school).college("공과대학").name("컴퓨터공학과").build());
        StudentProfile profile = onboardedStudent("7702", cs, 2023);

        studentCourseRepository.save(StudentCourse.builder()
                .studentProfile(profile).course(null).appliedDivision(null)
                .rawCourseCode(null).rawCourseName("미검수과목").credit(3)
                .takenYear(2023).takenSemester(Semester.FIRST)
                .status(CourseStatus.COMPLETED).source(RecordSource.PDF).isRetake(false).build());

        mockMvc.perform(get("/api/v1/planner")
                        .with(authentication(authenticationOf(profile.getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completedTerms.length()").value(0));
    }

    @Test
    void 휴학으로_수강_공백이_있어도_실제로_들은_학기_순서대로_학년학기가_매겨진다() throws Exception {
        School school = schoolRepository.save(School.builder().name("경희대학교-7706").build());
        Department cs = departmentRepository.save(Department.builder()
                .school(school).college("공과대학").name("컴퓨터공학과").build());
        Division majorRequired = divisionRepository.save(Division.builder()
                .school(school).code("04").category(DivisionCategory.MAJOR_REQUIRED).build());
        StudentProfile profile = onboardedStudent("7706", cs, 2023);

        studentCourseRepository.save(StudentCourse.builder()
                .studentProfile(profile).course(null).appliedDivision(majorRequired)
                .rawCourseCode(null).rawCourseName("자료구조").credit(3)
                .takenYear(2023).takenSemester(Semester.FIRST)
                .status(CourseStatus.COMPLETED).source(RecordSource.PDF).isRetake(false).build());
        studentCourseRepository.save(StudentCourse.builder()
                .studentProfile(profile).course(null).appliedDivision(majorRequired)
                .rawCourseCode(null).rawCourseName("알고리즘").credit(3)
                .takenYear(2023).takenSemester(Semester.SECOND)
                .status(CourseStatus.COMPLETED).source(RecordSource.PDF).isRetake(false).build());
        // 2024년은 휴학이라 STUDENT_COURSE 기록이 없음 - 달력으로 계산하면 3학년 1학기가 되지만
        // 실제로는 세 번째로 들은 학기라 2학년 1학기여야 한다.
        studentCourseRepository.save(StudentCourse.builder()
                .studentProfile(profile).course(null).appliedDivision(majorRequired)
                .rawCourseCode(null).rawCourseName("운영체제").credit(3)
                .takenYear(2025).takenSemester(Semester.FIRST)
                .status(CourseStatus.IN_PROGRESS).source(RecordSource.PDF).isRetake(false).build());

        mockMvc.perform(get("/api/v1/planner")
                        .with(authentication(authenticationOf(profile.getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completedTerms.length()").value(3))
                .andExpect(jsonPath("$.data.completedTerms[2].yearLevel").value(2))
                .andExpect(jsonPath("$.data.completedTerms[2].semester").value(1))
                .andExpect(jsonPath("$.data.completedTerms[2].name").value("2학년 1학기"))
                .andExpect(jsonPath("$.data.completedTerms[2].status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.completedTerms[2].courses[0].name").value("운영체제"));
    }

    @Test
    void 계획한_학기는_시뮬레이션_트리로_반환된다() throws Exception {
        School school = schoolRepository.save(School.builder().name("경희대학교-7703").build());
        Department iem = departmentRepository.save(Department.builder()
                .school(school).college("공과대학").name("산업경영공학과").build());
        Division majorRequired = divisionRepository.save(Division.builder()
                .school(school).code("04").category(DivisionCategory.MAJOR_REQUIRED).build());
        Course course1 = courseRepository.save(Course.builder()
                .school(school).courseCode("IEM201").name("경영정보시스템").credit(3)
                .offeringDepartment(iem).defaultDivision(majorRequired)
                .recommendedYearLow(2).recommendedYearHigh(2)
                .openedSemester(OpenedSemester.FIRST).isEnglish(false).isSw(false).build());
        Course course2 = courseRepository.save(Course.builder()
                .school(school).courseCode("IEM202").name("품질경영").credit(2)
                .offeringDepartment(iem)
                .openedSemester(OpenedSemester.FIRST).isEnglish(false).isSw(false).build());
        StudentProfile profile = onboardedStudent("7703", iem, 2023);

        PlannerSimulation simulation = plannerSimulationRepository.save(PlannerSimulation.builder()
                .studentProfile(profile).name("내 플래너").build());
        PlannerTerm term = plannerTermRepository.save(PlannerTerm.builder()
                .plannerSimulation(simulation).yearLevel(2).semester(1).build());
        PlannerTermVersion version = plannerTermVersionRepository.save(PlannerTermVersion.builder()
                .plannerTerm(term).versionNo(1).name("폴더 1").isSelected(true).versionOrder(0).build());
        plannerVersionItemRepository.save(PlannerVersionItem.builder()
                .plannerTermVersion(version).course(course1).plannedDivision(majorRequired)
                .credit(3).coursePositionOrder(0).build());
        // course2는 defaultDivision이 없는 과목이라 plannedDivision도 null로 저장된 케이스
        plannerVersionItemRepository.save(PlannerVersionItem.builder()
                .plannerTermVersion(version).course(course2).plannedDivision(null)
                .credit(2).coursePositionOrder(1).build());

        mockMvc.perform(get("/api/v1/planner")
                        .with(authentication(authenticationOf(profile.getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.plannedTerms.length()").value(1))
                .andExpect(jsonPath("$.data.plannedTerms[0].plannerTermId").value(term.getId()))
                .andExpect(jsonPath("$.data.plannedTerms[0].versions[0].isSelected").value(true))
                .andExpect(jsonPath("$.data.plannedTerms[0].versions[0].totalCredit").value(5))
                .andExpect(jsonPath("$.data.plannedTerms[0].versions[0].courses.length()").value(2))
                .andExpect(jsonPath("$.data.plannedTerms[0].versions[0].courses[0].name").value("경영정보시스템"))
                .andExpect(jsonPath("$.data.plannedTerms[0].versions[0].courses[0].divisionName").value("전공필수"))
                .andExpect(jsonPath("$.data.plannedTerms[0].versions[0].courses[1].courseId").value(course2.getId()))
                .andExpect(jsonPath("$.data.plannedTerms[0].versions[0].courses[1].divisionCategory").doesNotExist());
    }

    // isEnglish/isSw는 course 매칭 여부와 무관하게 항상 응답에 채워져야 하고(completedTerms는 매칭 안
    // 되면 false로 안전하게 떨어짐), plannedTerms는 course가 항상 존재하므로 그대로 반영돼야 한다.
    @Test
    void 이수완료와_계획_과목_둘_다_isEnglish_isSw가_반환된다() throws Exception {
        School school = schoolRepository.save(School.builder().name("경희대학교-7707").build());
        Department cs = departmentRepository.save(Department.builder()
                .school(school).college("공과대학").name("컴퓨터공학과").build());
        Division majorRequired = divisionRepository.save(Division.builder()
                .school(school).code("04").category(DivisionCategory.MAJOR_REQUIRED).build());
        Course englishSwCourse = courseRepository.save(Course.builder()
                .school(school).courseCode("CSE301").name("영어SW강의").credit(3)
                .offeringDepartment(cs).recommendedYearLow(3).recommendedYearHigh(3)
                .openedSemester(OpenedSemester.FIRST).isEnglish(true).isSw(true).build());
        StudentProfile profile = onboardedStudent("7707", cs, 2023);

        studentCourseRepository.save(StudentCourse.builder()
                .studentProfile(profile).course(englishSwCourse).appliedDivision(majorRequired)
                .rawCourseCode("CSE301").rawCourseName("영어SW강의").credit(3)
                .takenYear(2023).takenSemester(Semester.FIRST)
                .status(CourseStatus.COMPLETED).source(RecordSource.PDF).isRetake(false).build());
        // course 매칭 안 된 이수완료 과목은 isEnglish/isSw가 false로 안전하게 떨어져야 한다.
        studentCourseRepository.save(StudentCourse.builder()
                .studentProfile(profile).course(null).appliedDivision(majorRequired)
                .rawCourseCode(null).rawCourseName("미매칭과목").credit(3)
                .takenYear(2023).takenSemester(Semester.SECOND)
                .status(CourseStatus.COMPLETED).source(RecordSource.PDF).isRetake(false).build());

        PlannerSimulation simulation = plannerSimulationRepository.save(PlannerSimulation.builder()
                .studentProfile(profile).name("내 플래너").build());
        PlannerTerm term = plannerTermRepository.save(PlannerTerm.builder()
                .plannerSimulation(simulation).yearLevel(3).semester(1).build());
        PlannerTermVersion version = plannerTermVersionRepository.save(PlannerTermVersion.builder()
                .plannerTerm(term).versionNo(1).name("폴더 1").isSelected(true).versionOrder(0).build());
        plannerVersionItemRepository.save(PlannerVersionItem.builder()
                .plannerTermVersion(version).course(englishSwCourse).plannedDivision(majorRequired)
                .credit(3).coursePositionOrder(0).build());

        mockMvc.perform(get("/api/v1/planner")
                        .with(authentication(authenticationOf(profile.getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completedTerms[0].courses[0].isEnglish").value(true))
                .andExpect(jsonPath("$.data.completedTerms[0].courses[0].isSw").value(true))
                .andExpect(jsonPath("$.data.completedTerms[1].courses[0].isEnglish").value(false))
                .andExpect(jsonPath("$.data.completedTerms[1].courses[0].isSw").value(false))
                .andExpect(jsonPath("$.data.plannedTerms[0].versions[0].courses[0].isEnglish").value(true))
                .andExpect(jsonPath("$.data.plannedTerms[0].versions[0].courses[0].isSw").value(true));
    }

    @Test
    void 시뮬레이션을_한번도_저장한적_없으면_plannedTerms는_빈배열이다() throws Exception {
        School school = schoolRepository.save(School.builder().name("경희대학교-7704").build());
        Department cs = departmentRepository.save(Department.builder()
                .school(school).college("공과대학").name("컴퓨터공학과").build());
        StudentProfile profile = onboardedStudent("7704", cs, 2023);

        mockMvc.perform(get("/api/v1/planner")
                        .with(authentication(authenticationOf(profile.getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completedTerms.length()").value(0))
                .andExpect(jsonPath("$.data.plannedTerms.length()").value(0));
    }

    @Test
    void 프로필이_없으면_404_USER_003를_반환한다() throws Exception {
        Member member = memberRepository.save(Member.builder()
                .nickname("온보딩안함").oauthProvider(OauthProvider.KAKAO).oauthId("7705").email(null).build());

        mockMvc.perform(get("/api/v1/planner")
                        .with(authentication(authenticationOf(member.getId()))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_003"));
    }

    @Test
    void 인증_헤더가_없으면_401_CMN_005를_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/planner"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("CMN_005"));
    }

    @Test
    void 여름학기_과목이_1학기에_합산되지_않고_semester_3_별도_카드로_분리된다() throws Exception {
        School school = schoolRepository.save(School.builder().name("경희대학교-7710").build());
        Department cs = departmentRepository.save(Department.builder()
                .school(school).college("공과대학").name("컴퓨터공학과").build());
        Division majorRequired = divisionRepository.save(Division.builder()
                .school(school).code("04").category(DivisionCategory.MAJOR_REQUIRED).build());
        StudentProfile profile = onboardedStudent("7710", cs, 2023);

        studentCourseRepository.save(StudentCourse.builder()
                .studentProfile(profile).course(null).appliedDivision(majorRequired)
                .rawCourseCode(null).rawCourseName("자료구조").credit(3)
                .takenYear(2023).takenSemester(Semester.FIRST)
                .status(CourseStatus.COMPLETED).source(RecordSource.PDF).isRetake(false).build());
        studentCourseRepository.save(StudentCourse.builder()
                .studentProfile(profile).course(null).appliedDivision(majorRequired)
                .rawCourseCode(null).rawCourseName("여름특강").credit(2)
                .takenYear(2023).takenSemester(Semester.SUMMER)
                .status(CourseStatus.COMPLETED).source(RecordSource.PDF).isRetake(false).build());
        studentCourseRepository.save(StudentCourse.builder()
                .studentProfile(profile).course(null).appliedDivision(majorRequired)
                .rawCourseCode(null).rawCourseName("알고리즘").credit(3)
                .takenYear(2023).takenSemester(Semester.SECOND)
                .status(CourseStatus.COMPLETED).source(RecordSource.PDF).isRetake(false).build());

        mockMvc.perform(get("/api/v1/planner")
                        .with(authentication(authenticationOf(profile.getMember().getId()))))
                .andExpect(status().isOk())
                // 1학기, 여름학기, 2학기 — 3개 독립 카드
                .andExpect(jsonPath("$.data.completedTerms.length()").value(3))
                // 시간순: 1학기 → 여름 → 2학기
                .andExpect(jsonPath("$.data.completedTerms[0].semester").value(1))
                .andExpect(jsonPath("$.data.completedTerms[0].name").value("1학년 1학기"))
                .andExpect(jsonPath("$.data.completedTerms[0].totalCredit").value(3))
                .andExpect(jsonPath("$.data.completedTerms[1].semester").value(3))
                .andExpect(jsonPath("$.data.completedTerms[1].name").value("1학년 여름학기"))
                .andExpect(jsonPath("$.data.completedTerms[1].totalCredit").value(2))
                // 여름학기는 정규학기 카운트에 포함되지 않아 2학기도 여전히 1학년
                .andExpect(jsonPath("$.data.completedTerms[2].yearLevel").value(1))
                .andExpect(jsonPath("$.data.completedTerms[2].semester").value(2))
                .andExpect(jsonPath("$.data.completedTerms[2].name").value("1학년 2학기"));
    }

    @Test
    void 겨울학기_과목이_2학기에_합산되지_않고_semester_4_별도_카드로_분리된다() throws Exception {
        School school = schoolRepository.save(School.builder().name("경희대학교-7711").build());
        Department cs = departmentRepository.save(Department.builder()
                .school(school).college("공과대학").name("컴퓨터공학과").build());
        Division majorRequired = divisionRepository.save(Division.builder()
                .school(school).code("04").category(DivisionCategory.MAJOR_REQUIRED).build());
        StudentProfile profile = onboardedStudent("7711", cs, 2023);

        studentCourseRepository.save(StudentCourse.builder()
                .studentProfile(profile).course(null).appliedDivision(majorRequired)
                .rawCourseCode(null).rawCourseName("운영체제").credit(3)
                .takenYear(2023).takenSemester(Semester.SECOND)
                .status(CourseStatus.COMPLETED).source(RecordSource.PDF).isRetake(false).build());
        studentCourseRepository.save(StudentCourse.builder()
                .studentProfile(profile).course(null).appliedDivision(majorRequired)
                .rawCourseCode(null).rawCourseName("겨울특강").credit(2)
                .takenYear(2023).takenSemester(Semester.WINTER)
                .status(CourseStatus.COMPLETED).source(RecordSource.PDF).isRetake(false).build());

        mockMvc.perform(get("/api/v1/planner")
                        .with(authentication(authenticationOf(profile.getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completedTerms.length()").value(2))
                .andExpect(jsonPath("$.data.completedTerms[0].semester").value(2))
                .andExpect(jsonPath("$.data.completedTerms[0].name").value("1학년 2학기"))
                .andExpect(jsonPath("$.data.completedTerms[1].semester").value(4))
                .andExpect(jsonPath("$.data.completedTerms[1].name").value("1학년 겨울학기"))
                .andExpect(jsonPath("$.data.completedTerms[1].totalCredit").value(2));
    }

    @Test
    void 같은_연도에_네_학기_모두_있으면_시간순으로_정렬된다() throws Exception {
        School school = schoolRepository.save(School.builder().name("경희대학교-7712").build());
        Department cs = departmentRepository.save(Department.builder()
                .school(school).college("공과대학").name("컴퓨터공학과").build());
        Division majorRequired = divisionRepository.save(Division.builder()
                .school(school).code("04").category(DivisionCategory.MAJOR_REQUIRED).build());
        StudentProfile profile = onboardedStudent("7712", cs, 2023);

        // 저장 순서를 의도적으로 섞어 정렬 로직을 검증
        studentCourseRepository.save(StudentCourse.builder()
                .studentProfile(profile).course(null).appliedDivision(majorRequired)
                .rawCourseCode(null).rawCourseName("겨울과목").credit(1)
                .takenYear(2023).takenSemester(Semester.WINTER)
                .status(CourseStatus.COMPLETED).source(RecordSource.PDF).isRetake(false).build());
        studentCourseRepository.save(StudentCourse.builder()
                .studentProfile(profile).course(null).appliedDivision(majorRequired)
                .rawCourseCode(null).rawCourseName("2학기과목").credit(3)
                .takenYear(2023).takenSemester(Semester.SECOND)
                .status(CourseStatus.COMPLETED).source(RecordSource.PDF).isRetake(false).build());
        studentCourseRepository.save(StudentCourse.builder()
                .studentProfile(profile).course(null).appliedDivision(majorRequired)
                .rawCourseCode(null).rawCourseName("1학기과목").credit(3)
                .takenYear(2023).takenSemester(Semester.FIRST)
                .status(CourseStatus.COMPLETED).source(RecordSource.PDF).isRetake(false).build());
        studentCourseRepository.save(StudentCourse.builder()
                .studentProfile(profile).course(null).appliedDivision(majorRequired)
                .rawCourseCode(null).rawCourseName("여름과목").credit(2)
                .takenYear(2023).takenSemester(Semester.SUMMER)
                .status(CourseStatus.COMPLETED).source(RecordSource.PDF).isRetake(false).build());

        mockMvc.perform(get("/api/v1/planner")
                        .with(authentication(authenticationOf(profile.getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completedTerms.length()").value(4))
                // 시간순: 1학기(1) → 여름(3) → 2학기(2) → 겨울(4)
                .andExpect(jsonPath("$.data.completedTerms[0].semester").value(1))
                .andExpect(jsonPath("$.data.completedTerms[1].semester").value(3))
                .andExpect(jsonPath("$.data.completedTerms[2].semester").value(2))
                .andExpect(jsonPath("$.data.completedTerms[3].semester").value(4))
                // 계절학기는 정규학기 카운트에 미포함 → 4개 모두 1학년
                .andExpect(jsonPath("$.data.completedTerms[0].yearLevel").value(1))
                .andExpect(jsonPath("$.data.completedTerms[1].yearLevel").value(1))
                .andExpect(jsonPath("$.data.completedTerms[2].yearLevel").value(1))
                .andExpect(jsonPath("$.data.completedTerms[3].yearLevel").value(1));
    }

    // 배분이수교과(DISTRIBUTED_GE) 과목은 이수완료 카드에도 영역 정보(area)가 채워져야 한다(#256,
    // "이수구분별 과목 조회"의 area와 동일 규칙). 그 외 이수구분은 area가 null이어야 한다.
    @Test
    void 배분이수교과_이수완료_과목은_area_정보가_채워지고_그외_이수구분은_null이다() throws Exception {
        School school = schoolRepository.save(School.builder().name("경희대학교-7713").build());
        Department cs = departmentRepository.save(Department.builder()
                .school(school).college("공과대학").name("컴퓨터공학과").build());
        Division distributedGe = divisionRepository.save(Division.builder()
                .school(school).code("05").category(DivisionCategory.DISTRIBUTED_GE).build());
        Division majorRequired = divisionRepository.save(Division.builder()
                .school(school).code("04").category(DivisionCategory.MAJOR_REQUIRED).build());
        GeArea area = geAreaRepository.save(GeArea.builder()
                .school(school).code("AREA_3").name("상징, 문화, 소통").build());
        Course geCourse = courseRepository.save(Course.builder()
                .school(school).courseCode("GE301").name("미디어아트와문화").credit(3).geArea(area)
                .openedSemester(OpenedSemester.BOTH).isEnglish(false).isSw(false).build());
        Course majorCourse = courseRepository.save(Course.builder()
                .school(school).courseCode("CS301").name("운영체제").credit(3)
                .offeringDepartment(cs)
                .openedSemester(OpenedSemester.FIRST).isEnglish(false).isSw(false).build());
        StudentProfile profile = onboardedStudent("7713", cs, 2023);

        studentCourseRepository.save(StudentCourse.builder()
                .studentProfile(profile).course(geCourse).appliedDivision(distributedGe)
                .rawCourseCode("GE301").rawCourseName("미디어아트와문화").credit(3)
                .takenYear(2023).takenSemester(Semester.FIRST)
                .status(CourseStatus.COMPLETED).source(RecordSource.PDF).isRetake(false).build());
        studentCourseRepository.save(StudentCourse.builder()
                .studentProfile(profile).course(majorCourse).appliedDivision(majorRequired)
                .rawCourseCode("CS301").rawCourseName("운영체제").credit(3)
                .takenYear(2023).takenSemester(Semester.FIRST)
                .status(CourseStatus.COMPLETED).source(RecordSource.PDF).isRetake(false).build());

        mockMvc.perform(get("/api/v1/planner")
                        .with(authentication(authenticationOf(profile.getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completedTerms[0].courses[?(@.name=='미디어아트와문화')].area.code")
                        .value("AREA_3"))
                .andExpect(jsonPath("$.data.completedTerms[0].courses[?(@.name=='미디어아트와문화')].area.name")
                        .value("상징, 문화, 소통"))
                .andExpect(jsonPath("$.data.completedTerms[0].courses[?(@.name=='운영체제')].area").value(
                        org.hamcrest.Matchers.contains(org.hamcrest.Matchers.nullValue())));
    }

    // 계획 과목 쪽도 이수완료와 동일하게 배분이수교과일 때만 area가 채워져야 한다(#256).
    @Test
    void 배분이수교과_계획_과목도_area_정보가_채워진다() throws Exception {
        School school = schoolRepository.save(School.builder().name("경희대학교-7714").build());
        Department cs = departmentRepository.save(Department.builder()
                .school(school).college("공과대학").name("컴퓨터공학과").build());
        Division distributedGe = divisionRepository.save(Division.builder()
                .school(school).code("05").category(DivisionCategory.DISTRIBUTED_GE).build());
        GeArea area = geAreaRepository.save(GeArea.builder()
                .school(school).code("AREA_1").name("생명, 우주, 인간").build());
        Course geCourse = courseRepository.save(Course.builder()
                .school(school).courseCode("GE401").name("우주의이해").credit(3).geArea(area)
                .defaultDivision(distributedGe)
                .openedSemester(OpenedSemester.BOTH).isEnglish(false).isSw(false).build());
        StudentProfile profile = onboardedStudent("7714", cs, 2023);

        PlannerSimulation simulation = plannerSimulationRepository.save(PlannerSimulation.builder()
                .studentProfile(profile).name("내 플래너").build());
        PlannerTerm term = plannerTermRepository.save(PlannerTerm.builder()
                .plannerSimulation(simulation).yearLevel(2).semester(1).build());
        PlannerTermVersion version = plannerTermVersionRepository.save(PlannerTermVersion.builder()
                .plannerTerm(term).versionNo(1).name("폴더 1").isSelected(true).versionOrder(0).build());
        plannerVersionItemRepository.save(PlannerVersionItem.builder()
                .plannerTermVersion(version).course(geCourse).plannedDivision(distributedGe)
                .credit(3).coursePositionOrder(0).build());

        mockMvc.perform(get("/api/v1/planner")
                        .with(authentication(authenticationOf(profile.getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.plannedTerms[0].versions[0].courses[0].area.code").value("AREA_1"))
                .andExpect(jsonPath("$.data.plannedTerms[0].versions[0].courses[0].area.name").value("생명, 우주, 인간"));
    }
}
