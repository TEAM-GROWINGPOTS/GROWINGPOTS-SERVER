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
import com.growingpots.domain.university.entity.RequirementCourse;
import com.growingpots.domain.university.entity.RequirementCourseItem;
import com.growingpots.domain.university.entity.School;
import com.growingpots.domain.university.entity.enums.OpenedSemester;
import com.growingpots.domain.university.repository.CourseRepository;
import com.growingpots.domain.university.repository.DepartmentRepository;
import com.growingpots.domain.university.repository.RequirementCourseItemRepository;
import com.growingpots.domain.university.repository.RequirementCourseRepository;
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

// 학과 자체의 독립 졸업요건(division 기반 아님, 예: 스포츠의학과 졸업필수) 테스트.
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class GraduationRequiredTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private StudentProfileRepository studentProfileRepository;

    @Autowired
    private StudentMajorRepository studentMajorRepository;

    @Autowired
    private GraduationAnalysisSummaryRepository graduationAnalysisSummaryRepository;

    @Autowired
    private SchoolRepository schoolRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private RequirementCourseRepository requirementCourseRepository;

    @Autowired
    private RequirementCourseItemRepository requirementCourseItemRepository;

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

    private Authentication authenticationOf(Long memberId) {
        return new UsernamePasswordAuthenticationToken(memberId.toString(), null, Collections.emptyList());
    }

    // 스포츠의학과 학생 하나를 만들고, 전문실기1~6(2학점씩) + 맨손체조(1학점) 요건과 과목을 세팅한다.
    // 요건: "전문실기 2과목 이상"(minCount=2), "맨손체조"(minCount=1)
    private StudentProfile setUpSportsScienceStudent(String oauthId) {
        School school = schoolRepository.save(School.builder().name("경희대학교-" + oauthId).build());
        Department department = departmentRepository.save(Department.builder()
                .school(school).college("체육대학").name("스포츠의학과-" + oauthId).build());
        Member member = memberRepository.save(Member.builder()
                .nickname("테스트유저").oauthProvider(OauthProvider.KAKAO).oauthId(oauthId).email(null).build());
        StudentProfile profile = studentProfileRepository.save(StudentProfile.builder()
                .member(member).school(school).department(department).admissionYear(2023).build());
        StudentMajor major = studentMajorRepository.save(StudentMajor.builder()
                .studentProfile(profile).department(department).majorType(MajorType.MAIN).build());
        graduationAnalysisSummaryRepository.save(GraduationAnalysisSummary.builder().studentMajor(major).build());

        RequirementCourse specialty = requirementCourseRepository.save(RequirementCourse.builder()
                .department(department).division(null).name("전문실기")
                .baseYear(2019).minCredit(0).minCount(2).build());
        RequirementCourse gymnastics = requirementCourseRepository.save(RequirementCourse.builder()
                .department(department).division(null).name("맨손체조")
                .baseYear(2019).minCredit(0).minCount(1).build());

        for (int i = 1; i <= 6; i++) {
            Course course = courseRepository.save(Course.builder()
                    .school(school).courseCode("CPE" + (200 + i)).name("전문실기" + i).credit(2)
                    .recommendedYearLow(1).recommendedYearHigh(3).openedSemester(OpenedSemester.FIRST)
                    .isEnglish(false).isSw(false).isActive(true).build());
            requirementCourseItemRepository.save(RequirementCourseItem.builder()
                    .requirementCourse(specialty).course(course).build());
        }
        Course gym = courseRepository.save(Course.builder()
                .school(school).courseCode("CPE103").name("맨손체조").credit(1)
                .recommendedYearLow(1).recommendedYearHigh(2).openedSemester(OpenedSemester.BOTH)
                .isEnglish(false).isSw(false).isActive(true).build());
        requirementCourseItemRepository.save(RequirementCourseItem.builder()
                .requirementCourse(gymnastics).course(gym).build());

        return profile;
    }

    private void completeCourse(StudentProfile profile, String courseCode, int credit) {
        Course course = courseRepository.findBySchool(profile.getSchool()).stream()
                .filter(c -> courseCode.equals(c.getCourseCode())).findFirst().orElseThrow();
        studentCourseRepository.save(StudentCourse.builder()
                .studentProfile(profile).course(course).rawCourseCode(courseCode).rawCourseName(course.getName())
                .credit(credit).takenYear(2023).takenSemester(Semester.FIRST)
                .status(CourseStatus.COMPLETED).source(RecordSource.PDF).isRetake(false).build());
    }

    @Test
    void 전문실기_2과목과_맨손체조를_모두_이수하면_졸업필수를_만족한다() throws Exception {
        StudentProfile profile = setUpSportsScienceStudent("9201");
        completeCourse(profile, "CPE201", 2);
        completeCourse(profile, "CPE202", 2);
        completeCourse(profile, "CPE103", 1);

        mockMvc.perform(get("/api/v1/students/me/graduation/GRADUATION_REQUIRED/courses")
                        .param("department", "스포츠의학과-9201")
                        .with(authentication(authenticationOf(profile.getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.majors[0].satisfied").value(true))
                .andExpect(jsonPath("$.data.majors[0].hasRequiredList").value(true))
                .andExpect(jsonPath("$.data.majors[0].unmetDescriptions.length()").value(0))
                .andExpect(jsonPath("$.data.majors[0].courses.length()").value(7))
                .andExpect(jsonPath("$.data.majors[0].courses[?(@.name=='전문실기1')].taken").value(true))
                .andExpect(jsonPath("$.data.majors[0].courses[?(@.name=='맨손체조')].taken").value(true));

        mockMvc.perform(get("/api/v1/students/me/graduation")
                        .param("department", "스포츠의학과-9201")
                        .with(authentication(authenticationOf(profile.getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.graduatable").value(true))
                .andExpect(jsonPath("$.data.graduationRequired.hasGraduationRequired").value(true))
                .andExpect(jsonPath("$.data.graduationRequired.satisfied").value(true))
                .andExpect(jsonPath("$.data.graduationRequired.totalCredit").value(5))
                .andExpect(jsonPath("$.data.graduationRequired.items[?(@.name=='전문실기')].current").value(2))
                .andExpect(jsonPath("$.data.graduationRequired.items[?(@.name=='전문실기')].required").value(2))
                .andExpect(jsonPath("$.data.graduationRequired.items[?(@.name=='전문실기')].unit").value("COURSES"))
                .andExpect(jsonPath("$.data.graduationRequired.items[?(@.name=='전문실기')].satisfied").value(true))
                .andExpect(jsonPath("$.data.graduationRequired.items[?(@.name=='맨손체조')].current").value(1))
                .andExpect(jsonPath("$.data.graduationRequired.items[?(@.name=='맨손체조')].required").value(1));
    }

    @Test
    void 전문실기_1과목만_이수하고_맨손체조_미이수면_졸업필수를_만족하지_못한다() throws Exception {
        StudentProfile profile = setUpSportsScienceStudent("9202");
        completeCourse(profile, "CPE201", 2);

        mockMvc.perform(get("/api/v1/students/me/graduation/GRADUATION_REQUIRED/courses")
                        .param("department", "스포츠의학과-9202")
                        .with(authentication(authenticationOf(profile.getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.majors[0].satisfied").value(false))
                // 전문실기가 minCount 기준으로 바뀌면서 과목수 기준 조건이 되어 unmetDescriptions엔
                // 안 담긴다(과목 카드로만 표시) — 이하 courses 검증으로 충분히 확인됨.
                .andExpect(jsonPath("$.data.majors[0].unmetDescriptions.length()").value(0))
                .andExpect(jsonPath("$.data.majors[0].courses[?(@.name=='전문실기1')].taken").value(true))
                .andExpect(jsonPath("$.data.majors[0].courses[?(@.name=='전문실기2')].taken").value(false))
                .andExpect(jsonPath("$.data.majors[0].courses[?(@.name=='맨손체조')].taken").value(false));

        mockMvc.perform(get("/api/v1/students/me/graduation")
                        .param("department", "스포츠의학과-9202")
                        .with(authentication(authenticationOf(profile.getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.graduatable").value(false))
                .andExpect(jsonPath("$.data.graduationRequired.hasGraduationRequired").value(true))
                .andExpect(jsonPath("$.data.graduationRequired.satisfied").value(false))
                .andExpect(jsonPath("$.data.graduationRequired.totalCredit").value(2))
                .andExpect(jsonPath("$.data.graduationRequired.unmetDescriptions.length()").value(0))
                .andExpect(jsonPath("$.data.graduationRequired.items[?(@.name=='전문실기')].current").value(1))
                .andExpect(jsonPath("$.data.graduationRequired.items[?(@.name=='전문실기')].required").value(2))
                .andExpect(jsonPath("$.data.graduationRequired.items[?(@.name=='전문실기')].satisfied").value(false))
                .andExpect(jsonPath("$.data.graduationRequired.items[?(@.name=='맨손체조')].current").value(0))
                .andExpect(jsonPath("$.data.graduationRequired.items[?(@.name=='맨손체조')].required").value(1));
    }

    // 전문실기(과목수 기준)는 만족했지만 맨손체조(과목수 기준)만 미충족인 경우.
    // 둘 다 과목수 기준이라 unmetDescriptions는 항상 비어있고, current(만족한 조건 수)는 그대로
    // 반영돼 satisfied=false와 모순되지 않아야 한다(1/2, unmetDescriptions는 빈 리스트).
    @Test
    void 전문실기는_만족하고_맨손체조만_미이수면_current_required가_만족여부와_모순되지_않는다() throws Exception {
        StudentProfile profile = setUpSportsScienceStudent("9204");
        completeCourse(profile, "CPE201", 2);
        completeCourse(profile, "CPE202", 2);

        mockMvc.perform(get("/api/v1/students/me/graduation/GRADUATION_REQUIRED/courses")
                        .param("department", "스포츠의학과-9204")
                        .with(authentication(authenticationOf(profile.getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.majors[0].satisfied").value(false))
                .andExpect(jsonPath("$.data.majors[0].current").value(1))
                .andExpect(jsonPath("$.data.majors[0].required").value(2))
                .andExpect(jsonPath("$.data.majors[0].unmetDescriptions.length()").value(0));

        mockMvc.perform(get("/api/v1/students/me/graduation")
                        .param("department", "스포츠의학과-9204")
                        .with(authentication(authenticationOf(profile.getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.graduatable").value(false))
                .andExpect(jsonPath("$.data.graduationRequired.satisfied").value(false))
                .andExpect(jsonPath("$.data.graduationRequired.unmetDescriptions.length()").value(0));
    }

    // RequirementCourse row는 있는데 RequirementCourseItem(연결 과목)을 안 넣은 경우.
    // items()만 보면 비어있어서 "요건 없음"으로 오판할 수 있는데, totalRequirementCount()로
    // 판단해야 이 경우도 정확히 "요건 있음 + 미충족"으로 나온다.
    @Test
    void RequirementCourse는_있는데_연결과목이_없으면_요건이_있는_것으로_취급되고_미충족이다() throws Exception {
        School school = schoolRepository.save(School.builder().name("경희대학교-9205").build());
        Department department = departmentRepository.save(Department.builder()
                .school(school).college("체육대학").name("스포츠의학과-9205").build());
        Member member = memberRepository.save(Member.builder()
                .nickname("테스트유저").oauthProvider(OauthProvider.KAKAO).oauthId("9205").email(null).build());
        StudentProfile profile = studentProfileRepository.save(StudentProfile.builder()
                .member(member).school(school).department(department).admissionYear(2023).build());
        StudentMajor major = studentMajorRepository.save(StudentMajor.builder()
                .studentProfile(profile).department(department).majorType(MajorType.MAIN).build());
        graduationAnalysisSummaryRepository.save(GraduationAnalysisSummary.builder().studentMajor(major).build());

        // RequirementCourseItem을 아예 안 만듦 (연결 과목 누락 시나리오)
        requirementCourseRepository.save(RequirementCourse.builder()
                .department(department).division(null).name("졸업필수(미설정)")
                .baseYear(2019).minCredit(4).minCount(0).build());

        mockMvc.perform(get("/api/v1/students/me/graduation")
                        .param("department", "스포츠의학과-9205")
                        .with(authentication(authenticationOf(profile.getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.graduationRequired").exists())
                .andExpect(jsonPath("$.data.graduationRequired.hasGraduationRequired").value(true))
                .andExpect(jsonPath("$.data.graduationRequired.satisfied").value(false))
                .andExpect(jsonPath("$.data.graduatable").value(false));
    }

    // minCredit/minCount가 동시에 설정된 잘못된 시드 데이터가 들어오면, 어느 쪽을 쓸지 조용히
    // 정하지 말고 바로 실패해야 한다(데이터 실수를 즉시 드러내기 위함).
    @Test
    void minCredit과_minCount가_동시에_설정되면_예외가_발생한다() throws Exception {
        School school = schoolRepository.save(School.builder().name("경희대학교-9206").build());
        Department department = departmentRepository.save(Department.builder()
                .school(school).college("체육대학").name("스포츠의학과-9206").build());
        Member member = memberRepository.save(Member.builder()
                .nickname("테스트유저").oauthProvider(OauthProvider.KAKAO).oauthId("9206").email(null).build());
        StudentProfile profile = studentProfileRepository.save(StudentProfile.builder()
                .member(member).school(school).department(department).admissionYear(2023).build());
        StudentMajor major = studentMajorRepository.save(StudentMajor.builder()
                .studentProfile(profile).department(department).majorType(MajorType.MAIN).build());
        graduationAnalysisSummaryRepository.save(GraduationAnalysisSummary.builder().studentMajor(major).build());

        requirementCourseRepository.save(RequirementCourse.builder()
                .department(department).division(null).name("잘못된요건")
                .baseYear(2019).minCredit(4).minCount(1).build());

        mockMvc.perform(get("/api/v1/students/me/graduation")
                        .param("department", "스포츠의학과-9206")
                        .with(authentication(authenticationOf(profile.getMember().getId()))))
                .andExpect(status().is5xxServerError());
    }

    // graduationRequired는 그 학과에 실제 졸업필수 요건이 있을 때만 채워지고(예: 스포츠의학과),
    // 없으면 null - FE는 null 체크로 탭/카드 노출 여부를 판단한다.
    @Test
    void 졸업필수_요건이_없는_학과는_해당_섹션이_비어있다() throws Exception {
        School school = schoolRepository.save(School.builder().name("경희대학교-9203").build());
        Department department = departmentRepository.save(Department.builder()
                .school(school).college("공과대학").name("컴퓨터공학과-9203").build());
        Member member = memberRepository.save(Member.builder()
                .nickname("테스트유저").oauthProvider(OauthProvider.KAKAO).oauthId("9203").email(null).build());
        StudentProfile profile = studentProfileRepository.save(StudentProfile.builder()
                .member(member).school(school).department(department).admissionYear(2023).build());
        StudentMajor major = studentMajorRepository.save(StudentMajor.builder()
                .studentProfile(profile).department(department).majorType(MajorType.MAIN).build());
        graduationAnalysisSummaryRepository.save(GraduationAnalysisSummary.builder().studentMajor(major).build());

        mockMvc.perform(get("/api/v1/students/me/graduation/GRADUATION_REQUIRED/courses")
                        .param("department", "컴퓨터공학과-9203")
                        .with(authentication(authenticationOf(profile.getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.majors[0].hasRequiredList").value(false))
                .andExpect(jsonPath("$.data.majors[0].courses.length()").value(0));

        mockMvc.perform(get("/api/v1/students/me/graduation")
                        .param("department", "컴퓨터공학과-9203")
                        .with(authentication(authenticationOf(profile.getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.graduationRequired").doesNotExist());
    }

    // 전문실기 1과목만 완료하고 나머지 1과목을 플래너(선택된 버전)에 담아둔 경우,
    // source=PLANNED 조회 시 그 계획 과목까지 반영해서 전문실기가 충족돼야 한다.
    @Test
    void PLANNED_모드에서는_플래너에_담은_전문실기_과목도_반영된다() throws Exception {
        StudentProfile profile = setUpSportsScienceStudent("9207");
        completeCourse(profile, "CPE201", 2);

        Course cpe202 = courseRepository.findBySchool(profile.getSchool()).stream()
                .filter(c -> "CPE202".equals(c.getCourseCode())).findFirst().orElseThrow();

        PlannerSimulation simulation = plannerSimulationRepository.save(PlannerSimulation.builder()
                .studentProfile(profile).name("내 플래너").build());
        PlannerTerm term = plannerTermRepository.save(PlannerTerm.builder()
                .plannerSimulation(simulation).yearLevel(2).semester(1).build());
        PlannerTermVersion version = plannerTermVersionRepository.save(PlannerTermVersion.builder()
                .plannerTerm(term).versionNo(1).name("폴더 1").isSelected(true).versionOrder(0).build());
        plannerVersionItemRepository.save(PlannerVersionItem.builder()
                .plannerTermVersion(version).course(cpe202).plannedDivision(null)
                .credit(2).coursePositionOrder(0).build());

        // COMPLETED면 계획 과목이 반영되지 않아 여전히 미충족
        mockMvc.perform(get("/api/v1/students/me/graduation")
                        .param("department", "스포츠의학과-9207").param("source", "COMPLETED")
                        .with(authentication(authenticationOf(profile.getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.graduationRequired.satisfied").value(false))
                .andExpect(jsonPath("$.data.graduationRequired.items[?(@.name=='전문실기')].current").value(1));

        // PLANNED면 계획 과목(CPE202)까지 더해져 2/2로 충족
        mockMvc.perform(get("/api/v1/students/me/graduation")
                        .param("department", "스포츠의학과-9207").param("source", "PLANNED")
                        .with(authentication(authenticationOf(profile.getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.graduationRequired.items[?(@.name=='전문실기')].current").value(2))
                .andExpect(jsonPath("$.data.graduationRequired.items[?(@.name=='전문실기')].satisfied").value(true))
                .andExpect(jsonPath("$.data.graduationRequired.totalCredit").value(4));
    }

    // 예전엔 majors.stream().filter(DOUBLE).findFirst()로 첫 번째 복수전공만 쓰고 나머지는 조용히
    // 버렸던 버그의 회귀 테스트. 본전공 1개 + 복수전공 2개인 학생이 majorType=ALL로 조회하면
    // sections.majors에 3개 전공이 전부(누락 없이) 나와야 한다.
    @Test
    void 복수전공을_여러_개_가진_학생은_ALL_조회_시_전공이_전부_나온다() throws Exception {
        School school = schoolRepository.save(School.builder().name("경희대학교-9208").build());
        Department mainDept = departmentRepository.save(Department.builder()
                .school(school).college("공과대학").name("컴퓨터공학과-9208").build());
        Department doubleDept1 = departmentRepository.save(Department.builder()
                .school(school).college("경영대학").name("경영학과-9208").build());
        Department doubleDept2 = departmentRepository.save(Department.builder()
                .school(school).college("공과대학").name("화학공학과-9208").build());
        Member member = memberRepository.save(Member.builder()
                .nickname("테스트유저").oauthProvider(OauthProvider.KAKAO).oauthId("9208").email(null).build());
        StudentProfile profile = studentProfileRepository.save(StudentProfile.builder()
                .member(member).school(school).department(mainDept).admissionYear(2023).build());

        StudentMajor main = studentMajorRepository.save(StudentMajor.builder()
                .studentProfile(profile).department(mainDept).majorType(MajorType.MAIN).build());
        StudentMajor double1 = studentMajorRepository.save(StudentMajor.builder()
                .studentProfile(profile).department(doubleDept1).majorType(MajorType.DOUBLE).build());
        StudentMajor double2 = studentMajorRepository.save(StudentMajor.builder()
                .studentProfile(profile).department(doubleDept2).majorType(MajorType.DOUBLE).build());
        graduationAnalysisSummaryRepository.save(GraduationAnalysisSummary.builder().studentMajor(main).build());
        graduationAnalysisSummaryRepository.save(GraduationAnalysisSummary.builder().studentMajor(double1).build());
        graduationAnalysisSummaryRepository.save(GraduationAnalysisSummary.builder().studentMajor(double2).build());

        mockMvc.perform(get("/api/v1/students/me/graduation")
                        .param("majorType", "ALL")
                        .with(authentication(authenticationOf(profile.getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sections.majors.length()").value(3))
                .andExpect(jsonPath("$.data.sections.majors[?(@.majorName=='컴퓨터공학과-9208')].majorType").value("MAIN"))
                .andExpect(jsonPath("$.data.sections.majors[?(@.majorName=='경영학과-9208')].majorType").value("DOUBLE"))
                .andExpect(jsonPath("$.data.sections.majors[?(@.majorName=='화학공학과-9208')].majorType").value("DOUBLE"))
                // 셋 다 졸업필수 요건이 없는 학과라 graduationRequired가 전공별로 하나도 없어야 한다.
                // JsonPath 필터([?(...)])는 매칭 결과를 배열로 감싸서 null이 [null]로 나오는 바람에
                // doesNotExist()가 안 먹어서, 등록 순서를 아는 인덱스로 직접 접근한다
                // (0=컴퓨터공학과 MAIN, 1=경영학과 DOUBLE, 2=화학공학과 DOUBLE).
                .andExpect(jsonPath("$.data.sections.majors[0].graduationRequired").doesNotExist())
                .andExpect(jsonPath("$.data.sections.majors[1].graduationRequired").doesNotExist())
                .andExpect(jsonPath("$.data.sections.majors[2].graduationRequired").doesNotExist());

        // department 파라미터로 복수전공 중 하나(두 번째로 등록된 것)만 콕 집어 조회도 되는지 확인
        mockMvc.perform(get("/api/v1/students/me/graduation")
                        .param("department", "화학공학과-9208")
                        .with(authentication(authenticationOf(profile.getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sections").doesNotExist())
                .andExpect(jsonPath("$.data.conditions").isArray());
    }

    // 복수전공 중 하나(본전공이 아니어도)에만 졸업필수 요건이 있으면, majors 배열에서 그 전공
    // 항목에만 graduationRequired가 채워지고 나머지 전공은 null이어야 한다.
    @Test
    void 복수전공_중_하나에만_졸업필수가_있으면_그_전공에만_반영된다() throws Exception {
        StudentProfile sportsProfile = setUpSportsScienceStudent("9209");
        completeCourse(sportsProfile, "CPE201", 2);
        completeCourse(sportsProfile, "CPE202", 2);
        completeCourse(sportsProfile, "CPE103", 1);

        Department mainDept = departmentRepository.save(Department.builder()
                .school(sportsProfile.getSchool()).college("공과대학").name("화학공학과-9209").build());
        Department sportsDept = departmentRepository.findAll().stream()
                .filter(d -> "스포츠의학과-9209".equals(d.getName())).findFirst().orElseThrow();

        // 스포츠의학과를 본전공이 아니라 두 번째 복수전공으로 등록해, majors 배열 순서와 무관하게
        // 잘 찾아내는지 확인한다. 기존 MAIN 학생전공(스포츠의학과)을 DOUBLE로 남겨두는 대신, 별도
        // 학생을 새로 만들어 화학공학과를 본전공으로 하고 스포츠의학과를 복수전공으로 추가한다.
        Member member = memberRepository.save(Member.builder()
                .nickname("테스트유저").oauthProvider(OauthProvider.KAKAO).oauthId("9209b").email(null).build());
        StudentProfile profile = studentProfileRepository.save(StudentProfile.builder()
                .member(member).school(sportsProfile.getSchool()).department(mainDept).admissionYear(2023).build());
        StudentMajor main = studentMajorRepository.save(StudentMajor.builder()
                .studentProfile(profile).department(mainDept).majorType(MajorType.MAIN).build());
        StudentMajor sportsDouble = studentMajorRepository.save(StudentMajor.builder()
                .studentProfile(profile).department(sportsDept).majorType(MajorType.DOUBLE).build());
        graduationAnalysisSummaryRepository.save(GraduationAnalysisSummary.builder().studentMajor(main).build());
        graduationAnalysisSummaryRepository.save(GraduationAnalysisSummary.builder().studentMajor(sportsDouble).build());
        completeCourse(profile, "CPE201", 2);
        completeCourse(profile, "CPE202", 2);
        completeCourse(profile, "CPE103", 1);

        mockMvc.perform(get("/api/v1/students/me/graduation")
                        .param("majorType", "ALL")
                        .with(authentication(authenticationOf(profile.getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sections.majors.length()").value(2))
                // 0=화학공학과(MAIN, 요건 없음), 1=스포츠의학과(DOUBLE, 요건 있음)
                .andExpect(jsonPath("$.data.sections.majors[0].majorName").value("화학공학과-9209"))
                .andExpect(jsonPath("$.data.sections.majors[0].graduationRequired").doesNotExist())
                .andExpect(jsonPath("$.data.sections.majors[1].majorName").value("스포츠의학과-9209"))
                .andExpect(jsonPath("$.data.sections.majors[1].graduationRequired.hasGraduationRequired").value(true))
                .andExpect(jsonPath("$.data.sections.majors[1].graduationRequired.satisfied").value(true))
                .andExpect(jsonPath("$.data.sections.majors[1].graduationRequired.items[?(@.name=='전문실기')].current").value(2));
    }

    // GENERAL_ELECTIVE(기타)는 요구 학점 기준 자체가 없어(required=null) 예전엔 satisfied=true로
    // 나왔는데, 졸업 요건이 아니라 참고용 집계라 "충족" 배지를 안 보여주기로 해서 무조건 false로
    // 고정했다. OTHERS 탭과 드릴다운(GENERAL_ELECTIVE/courses) 둘 다 확인한다.
    @Test
    void 기타_이수구분은_학점과_무관하게_satisfied가_항상_false다() throws Exception {
        School school = schoolRepository.save(School.builder().name("경희대학교-9210").build());
        Department department = departmentRepository.save(Department.builder()
                .school(school).college("공과대학").name("화학공학과-9210").build());
        Member member = memberRepository.save(Member.builder()
                .nickname("테스트유저").oauthProvider(OauthProvider.KAKAO).oauthId("9210").email(null).build());
        StudentProfile profile = studentProfileRepository.save(StudentProfile.builder()
                .member(member).school(school).department(department).admissionYear(2023).build());
        StudentMajor major = studentMajorRepository.save(StudentMajor.builder()
                .studentProfile(profile).department(department).majorType(MajorType.MAIN).build());
        graduationAnalysisSummaryRepository.save(GraduationAnalysisSummary.builder().studentMajor(major).build());

        mockMvc.perform(get("/api/v1/students/me/graduation")
                        .param("majorType", "OTHERS")
                        .with(authentication(authenticationOf(profile.getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.conditions[0].code").value("GENERAL_ELECTIVE"))
                .andExpect(jsonPath("$.data.conditions[0].satisfied").value(false));

        mockMvc.perform(get("/api/v1/students/me/graduation/GENERAL_ELECTIVE/courses")
                        .param("department", "화학공학과-9210")
                        .with(authentication(authenticationOf(profile.getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.majors[0].satisfied").value(false));
    }
}
