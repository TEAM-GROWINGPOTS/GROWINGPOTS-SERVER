package com.growingpots.domain.planner.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import com.growingpots.domain.university.entity.School;
import com.growingpots.domain.university.entity.enums.DivisionCategory;
import com.growingpots.domain.university.entity.enums.OpenedSemester;
import com.growingpots.domain.university.repository.CourseRepository;
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

// 재수강 과목 학점 계산 로직 테스트
// 1. GET /api/v1/planner — 학기카드 totalCredit에서 DIMMED 항목 제외
// 2. PUT /api/v1/planner — hasDuplicateCourse (새로 추가된 과목 기준)
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class PlannerRetakeTest {

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

    private Authentication authOf(Long memberId) {
        return new UsernamePasswordAuthenticationToken(memberId.toString(), null, Collections.emptyList());
    }

    // ─── GET /api/v1/planner — totalCredit ──────────────────────────────────

    // 재수강 과목이 1-1과 2-1에 모두 담긴 경우:
    // - 1-1 버전: 재수강 과목 DIMMED → totalCredit에 포함 안 됨
    // - 2-1 버전: 재수강 과목 BADGE  → totalCredit에 포함됨
    @Test
    void 재수강과목이_여러학기에_담기면_DIMMED학기_버전의_totalCredit에서_제외된다() throws Exception {
        School school = schoolRepository.save(School.builder().name("경희대학교-8801").build());
        Department cs = departmentRepository.save(Department.builder()
                .school(school).college("공과대학").name("컴퓨터공학과").build());
        Division majorRequired = divisionRepository.save(Division.builder()
                .school(school).code("04").category(DivisionCategory.MAJOR_REQUIRED).build());

        // 재수강 대상 과목 (이미 COMPLETED)
        Course retakeCourse = courseRepository.save(Course.builder()
                .school(school).courseCode("CS301").name("알고리즘").credit(3)
                .offeringDepartment(cs).defaultDivision(majorRequired)
                .openedSemester(OpenedSemester.BOTH).isEnglish(false).isSw(false).build());
        // 일반 과목 (재수강 아님)
        Course normalCourse = courseRepository.save(Course.builder()
                .school(school).courseCode("CS302").name("자료구조").credit(3)
                .offeringDepartment(cs).defaultDivision(majorRequired)
                .openedSemester(OpenedSemester.BOTH).isEnglish(false).isSw(false).build());

        StudentProfile profile = onboardedStudent("8801", cs);

        // retakeCourse는 이미 이수완료 상태
        studentCourseRepository.save(StudentCourse.builder()
                .studentProfile(profile).course(retakeCourse).appliedDivision(majorRequired)
                .rawCourseCode("CS301").rawCourseName("알고리즘").credit(3)
                .takenYear(2022).takenSemester(Semester.FIRST)
                .status(CourseStatus.COMPLETED).source(RecordSource.PDF).isRetake(false).build());

        // 플래너: 1-1에 retakeCourse(DIMMED 예상) + normalCourse, 2-1에 retakeCourse(BADGE 예상)
        PlannerSimulation simulation = plannerSimulationRepository.save(PlannerSimulation.builder()
                .studentProfile(profile).name("내 플래너").build());

        PlannerTerm term11 = plannerTermRepository.save(PlannerTerm.builder()
                .plannerSimulation(simulation).yearLevel(1).semester(1).build());
        PlannerTermVersion version11 = plannerTermVersionRepository.save(PlannerTermVersion.builder()
                .plannerTerm(term11).versionNo(1).name("폴더 1").isSelected(true).versionOrder(0).build());
        plannerVersionItemRepository.save(PlannerVersionItem.builder()
                .plannerTermVersion(version11).course(retakeCourse).plannedDivision(majorRequired)
                .credit(3).coursePositionOrder(0).build());
        plannerVersionItemRepository.save(PlannerVersionItem.builder()
                .plannerTermVersion(version11).course(normalCourse).plannedDivision(majorRequired)
                .credit(3).coursePositionOrder(1).build());

        PlannerTerm term21 = plannerTermRepository.save(PlannerTerm.builder()
                .plannerSimulation(simulation).yearLevel(2).semester(1).build());
        PlannerTermVersion version21 = plannerTermVersionRepository.save(PlannerTermVersion.builder()
                .plannerTerm(term21).versionNo(1).name("폴더 1").isSelected(true).versionOrder(0).build());
        plannerVersionItemRepository.save(PlannerVersionItem.builder()
                .plannerTermVersion(version21).course(retakeCourse).plannedDivision(majorRequired)
                .credit(3).coursePositionOrder(0).build());

        mockMvc.perform(get("/api/v1/planner")
                        .with(authentication(authOf(profile.getMember().getId()))))
                .andExpect(status().isOk())
                // 1-1: retakeCourse(DIMMED, 3학점 제외) + normalCourse(3학점) = 3학점
                .andExpect(jsonPath("$.data.plannedTerms[0].yearLevel").value(1))
                .andExpect(jsonPath("$.data.plannedTerms[0].versions[0].totalCredit").value(3))
                .andExpect(jsonPath("$.data.plannedTerms[0].versions[0].courses[0].retakeDisplay").value("DIMMED"))
                // 2-1: retakeCourse(BADGE, 3학점 포함) = 3학점
                .andExpect(jsonPath("$.data.plannedTerms[1].yearLevel").value(2))
                .andExpect(jsonPath("$.data.plannedTerms[1].versions[0].totalCredit").value(3))
                .andExpect(jsonPath("$.data.plannedTerms[1].versions[0].courses[0].retakeDisplay").value("BADGE"));
    }

    // 같은 학기 내 다른 버전(폴더)에 같은 재수강 과목이 있는 경우:
    // 각 버전은 독립적이므로 두 버전 모두 totalCredit에 포함
    @Test
    void 재수강과목이_같은학기_다른버전에_있으면_각_버전_totalCredit에_모두_포함된다() throws Exception {
        School school = schoolRepository.save(School.builder().name("경희대학교-8802").build());
        Department cs = departmentRepository.save(Department.builder()
                .school(school).college("공과대학").name("컴퓨터공학과").build());
        Division majorRequired = divisionRepository.save(Division.builder()
                .school(school).code("04").category(DivisionCategory.MAJOR_REQUIRED).build());

        Course retakeCourse = courseRepository.save(Course.builder()
                .school(school).courseCode("CS401").name("운영체제").credit(3)
                .offeringDepartment(cs).defaultDivision(majorRequired)
                .openedSemester(OpenedSemester.BOTH).isEnglish(false).isSw(false).build());

        StudentProfile profile = onboardedStudent("8802", cs);

        studentCourseRepository.save(StudentCourse.builder()
                .studentProfile(profile).course(retakeCourse).appliedDivision(majorRequired)
                .rawCourseCode("CS401").rawCourseName("운영체제").credit(3)
                .takenYear(2022).takenSemester(Semester.SECOND)
                .status(CourseStatus.COMPLETED).source(RecordSource.PDF).isRetake(false).build());

        // 1-1에 버전1, 버전2 모두 retakeCourse 포함
        PlannerSimulation simulation = plannerSimulationRepository.save(PlannerSimulation.builder()
                .studentProfile(profile).name("내 플래너").build());
        PlannerTerm term11 = plannerTermRepository.save(PlannerTerm.builder()
                .plannerSimulation(simulation).yearLevel(1).semester(1).build());

        PlannerTermVersion version1 = plannerTermVersionRepository.save(PlannerTermVersion.builder()
                .plannerTerm(term11).versionNo(1).name("폴더 1").isSelected(true).versionOrder(0).build());
        plannerVersionItemRepository.save(PlannerVersionItem.builder()
                .plannerTermVersion(version1).course(retakeCourse).plannedDivision(majorRequired)
                .credit(3).coursePositionOrder(0).build());

        PlannerTermVersion version2 = plannerTermVersionRepository.save(PlannerTermVersion.builder()
                .plannerTerm(term11).versionNo(2).name("폴더 2").isSelected(false).versionOrder(1).build());
        plannerVersionItemRepository.save(PlannerVersionItem.builder()
                .plannerTermVersion(version2).course(retakeCourse).plannedDivision(majorRequired)
                .credit(3).coursePositionOrder(0).build());

        mockMvc.perform(get("/api/v1/planner")
                        .with(authentication(authOf(profile.getMember().getId()))))
                .andExpect(status().isOk())
                // 같은 학기 내 두 버전 모두 BADGE → totalCredit에 모두 포함
                .andExpect(jsonPath("$.data.plannedTerms[0].versions[0].totalCredit").value(3))
                .andExpect(jsonPath("$.data.plannedTerms[0].versions[0].courses[0].retakeDisplay").value("BADGE"))
                .andExpect(jsonPath("$.data.plannedTerms[0].versions[1].totalCredit").value(3))
                .andExpect(jsonPath("$.data.plannedTerms[0].versions[1].courses[0].retakeDisplay").value("BADGE"));
    }

    // ─── PUT /api/v1/planner — hasDuplicateCourse ───────────────────────────

    // 플래너에 없던 과목인데 이미 COMPLETED 상태이면 hasDuplicateCourse = true
    @Test
    void 이미_이수완료된_과목을_새로_플래너에_추가하면_hasDuplicateCourse가_true이다() throws Exception {
        School school = schoolRepository.save(School.builder().name("경희대학교-8803").build());
        Department cs = departmentRepository.save(Department.builder()
                .school(school).college("공과대학").name("컴퓨터공학과").build());
        Division majorRequired = divisionRepository.save(Division.builder()
                .school(school).code("04").category(DivisionCategory.MAJOR_REQUIRED).build());
        Course completedCourse = courseRepository.save(Course.builder()
                .school(school).courseCode("CS501").name("데이터베이스").credit(3)
                .offeringDepartment(cs).defaultDivision(majorRequired)
                .openedSemester(OpenedSemester.BOTH).isEnglish(false).isSw(false).build());

        StudentProfile profile = onboardedStudent("8803", cs);

        // 이미 이수완료 상태
        studentCourseRepository.save(StudentCourse.builder()
                .studentProfile(profile).course(completedCourse).appliedDivision(majorRequired)
                .rawCourseCode("CS501").rawCourseName("데이터베이스").credit(3)
                .takenYear(2023).takenSemester(Semester.FIRST)
                .status(CourseStatus.COMPLETED).source(RecordSource.PDF).isRetake(false).build());

        // 기존 플래너: 비어있음 (PUT으로 처음 추가)
        String requestBody = """
                {
                  "plannerSimulationId": null,
                  "terms": [
                    {
                      "yearLevel": 2,
                      "semester": 1,
                      "versions": [
                        {
                          "versionNo": 1,
                          "name": "폴더 1",
                          "isSelected": true,
                          "versionOrder": 0,
                          "items": [
                            { "courseId": %d, "coursePositionOrder": 0 }
                          ]
                        }
                      ]
                    }
                  ]
                }
                """.formatted(completedCourse.getId());

        mockMvc.perform(put("/api/v1/planner")
                        .with(authentication(authOf(profile.getMember().getId())))
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasDuplicateCourse").value(true));
    }

    // 플래너에 없던 과목인데 이미 IN_PROGRESS 상태이면 hasDuplicateCourse = true
    @Test
    void 현재_수강중인_과목을_새로_플래너에_추가하면_hasDuplicateCourse가_true이다() throws Exception {
        School school = schoolRepository.save(School.builder().name("경희대학교-8804").build());
        Department cs = departmentRepository.save(Department.builder()
                .school(school).college("공과대학").name("컴퓨터공학과").build());
        Division majorRequired = divisionRepository.save(Division.builder()
                .school(school).code("04").category(DivisionCategory.MAJOR_REQUIRED).build());
        Course inProgressCourse = courseRepository.save(Course.builder()
                .school(school).courseCode("CS601").name("소프트웨어공학").credit(3)
                .offeringDepartment(cs).defaultDivision(majorRequired)
                .openedSemester(OpenedSemester.BOTH).isEnglish(false).isSw(false).build());

        StudentProfile profile = onboardedStudent("8804", cs);

        // 현재 수강중 상태
        studentCourseRepository.save(StudentCourse.builder()
                .studentProfile(profile).course(inProgressCourse).appliedDivision(majorRequired)
                .rawCourseCode("CS601").rawCourseName("소프트웨어공학").credit(3)
                .takenYear(2024).takenSemester(Semester.FIRST)
                .status(CourseStatus.IN_PROGRESS).source(RecordSource.PDF).isRetake(false).build());

        String requestBody = """
                {
                  "plannerSimulationId": null,
                  "terms": [
                    {
                      "yearLevel": 3,
                      "semester": 1,
                      "versions": [
                        {
                          "versionNo": 1,
                          "name": "폴더 1",
                          "isSelected": true,
                          "versionOrder": 0,
                          "items": [
                            { "courseId": %d, "coursePositionOrder": 0 }
                          ]
                        }
                      ]
                    }
                  ]
                }
                """.formatted(inProgressCourse.getId());

        mockMvc.perform(put("/api/v1/planner")
                        .with(authentication(authOf(profile.getMember().getId())))
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasDuplicateCourse").value(true));
    }

    // 기존 플래너에 이미 있던 재수강 과목을 그대로 유지하는 경우 (새로 추가 아님):
    // hasDuplicateCourse = false (새로 추가된 과목 기준이기 때문)
    @Test
    void 기존_플래너에_이미_있던_재수강과목을_그대로_유지하면_hasDuplicateCourse가_false이다() throws Exception {
        School school = schoolRepository.save(School.builder().name("경희대학교-8805").build());
        Department cs = departmentRepository.save(Department.builder()
                .school(school).college("공과대학").name("컴퓨터공학과").build());
        Division majorRequired = divisionRepository.save(Division.builder()
                .school(school).code("04").category(DivisionCategory.MAJOR_REQUIRED).build());
        Course completedCourse = courseRepository.save(Course.builder()
                .school(school).courseCode("CS701").name("컴퓨터네트워크").credit(3)
                .offeringDepartment(cs).defaultDivision(majorRequired)
                .openedSemester(OpenedSemester.BOTH).isEnglish(false).isSw(false).build());

        StudentProfile profile = onboardedStudent("8805", cs);

        studentCourseRepository.save(StudentCourse.builder()
                .studentProfile(profile).course(completedCourse).appliedDivision(majorRequired)
                .rawCourseCode("CS701").rawCourseName("컴퓨터네트워크").credit(3)
                .takenYear(2023).takenSemester(Semester.SECOND)
                .status(CourseStatus.COMPLETED).source(RecordSource.PDF).isRetake(false).build());

        Authentication auth = authOf(profile.getMember().getId());

        String requestBody = """
                {
                  "plannerSimulationId": null,
                  "terms": [
                    {
                      "yearLevel": 2,
                      "semester": 1,
                      "versions": [
                        {
                          "versionNo": 1,
                          "name": "폴더 1",
                          "isSelected": true,
                          "versionOrder": 0,
                          "items": [
                            { "courseId": %d, "coursePositionOrder": 0 }
                          ]
                        }
                      ]
                    }
                  ]
                }
                """.formatted(completedCourse.getId());

        // 1차 저장: completedCourse를 처음 추가 → hasDuplicateCourse = true
        mockMvc.perform(put("/api/v1/planner")
                        .with(authentication(auth))
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasDuplicateCourse").value(true));

        // 2차 저장: completedCourse 그대로 유지 (새로 추가 아님) → hasDuplicateCourse = false
        mockMvc.perform(put("/api/v1/planner")
                        .with(authentication(auth))
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasDuplicateCourse").value(false));
    }

    // 이수 이력이 없는 순수 신규 과목만 추가하면 hasDuplicateCourse = false
    @Test
    void 미이수_과목만_새로_추가하면_hasDuplicateCourse가_false이다() throws Exception {
        School school = schoolRepository.save(School.builder().name("경희대학교-8806").build());
        Department cs = departmentRepository.save(Department.builder()
                .school(school).college("공과대학").name("컴퓨터공학과").build());
        Division majorRequired = divisionRepository.save(Division.builder()
                .school(school).code("04").category(DivisionCategory.MAJOR_REQUIRED).build());
        Course newCourse = courseRepository.save(Course.builder()
                .school(school).courseCode("CS801").name("머신러닝").credit(3)
                .offeringDepartment(cs).defaultDivision(majorRequired)
                .openedSemester(OpenedSemester.BOTH).isEnglish(false).isSw(false).build());

        // studentCourse 없음 (이수 이력 없음)
        StudentProfile profile = onboardedStudent("8806", cs);

        String requestBody = """
                {
                  "plannerSimulationId": null,
                  "terms": [
                    {
                      "yearLevel": 3,
                      "semester": 2,
                      "versions": [
                        {
                          "versionNo": 1,
                          "name": "폴더 1",
                          "isSelected": true,
                          "versionOrder": 0,
                          "items": [
                            { "courseId": %d, "coursePositionOrder": 0 }
                          ]
                        }
                      ]
                    }
                  ]
                }
                """.formatted(newCourse.getId());

        mockMvc.perform(put("/api/v1/planner")
                        .with(authentication(authOf(profile.getMember().getId())))
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasDuplicateCourse").value(false));
    }

    // 선택 버전(isSelected=true)에 없는 과목을 비선택 버전에만 재수강 과목을 담아도
    // hasDuplicateCourse = false (비선택 버전은 판정 대상 제외)
    @Test
    void 비선택버전에만_재수강과목이_있으면_hasDuplicateCourse가_false이다() throws Exception {
        School school = schoolRepository.save(School.builder().name("경희대학교-8807").build());
        Department cs = departmentRepository.save(Department.builder()
                .school(school).college("공과대학").name("컴퓨터공학과").build());
        Division majorRequired = divisionRepository.save(Division.builder()
                .school(school).code("04").category(DivisionCategory.MAJOR_REQUIRED).build());
        Course completedCourse = courseRepository.save(Course.builder()
                .school(school).courseCode("CS901").name("컴파일러").credit(3)
                .offeringDepartment(cs).defaultDivision(majorRequired)
                .openedSemester(OpenedSemester.BOTH).isEnglish(false).isSw(false).build());
        Course newCourse = courseRepository.save(Course.builder()
                .school(school).courseCode("CS902").name("임베디드시스템").credit(3)
                .offeringDepartment(cs).defaultDivision(majorRequired)
                .openedSemester(OpenedSemester.BOTH).isEnglish(false).isSw(false).build());

        StudentProfile profile = onboardedStudent("8807", cs);

        studentCourseRepository.save(StudentCourse.builder()
                .studentProfile(profile).course(completedCourse).appliedDivision(majorRequired)
                .rawCourseCode("CS901").rawCourseName("컴파일러").credit(3)
                .takenYear(2023).takenSemester(Semester.FIRST)
                .status(CourseStatus.COMPLETED).source(RecordSource.PDF).isRetake(false).build());

        // 선택 버전(isSelected=true): 신규 과목만 포함
        // 비선택 버전(isSelected=false): 재수강 과목(completedCourse) 포함
        String requestBody = """
                {
                  "plannerSimulationId": null,
                  "terms": [
                    {
                      "yearLevel": 2,
                      "semester": 1,
                      "versions": [
                        {
                          "versionNo": 1,
                          "name": "폴더 1",
                          "isSelected": true,
                          "versionOrder": 0,
                          "items": [
                            { "courseId": %d, "coursePositionOrder": 0 }
                          ]
                        },
                        {
                          "versionNo": 2,
                          "name": "폴더 2",
                          "isSelected": false,
                          "versionOrder": 1,
                          "items": [
                            { "courseId": %d, "coursePositionOrder": 0 }
                          ]
                        }
                      ]
                    }
                  ]
                }
                """.formatted(newCourse.getId(), completedCourse.getId());

        mockMvc.perform(put("/api/v1/planner")
                        .with(authentication(authOf(profile.getMember().getId())))
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isOk())
                // 선택 버전에 재수강 과목 없으므로 false
                .andExpect(jsonPath("$.data.hasDuplicateCourse").value(false));
    }
}
