package com.growingpots.domain.planner.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.growingpots.domain.planner.entity.PlannerVersionItem;
import com.growingpots.domain.planner.repository.PlannerSimulationRepository;
import com.growingpots.domain.planner.repository.PlannerVersionItemRepository;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
class PlannerSaveTest {

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
    private PlannerSimulationRepository plannerSimulationRepository;

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

    private String singleCoursePlannerBody(Long simulationId, Long courseId) {
        return """
                {
                  "plannerSimulationId": %s,
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
                """.formatted(simulationId == null ? "null" : simulationId, courseId);
    }

    @Test
    void 타전공인정과목을_추가하면_학생_학과_기준_인정_이수구분이_저장된다() throws Exception {
        School school = schoolRepository.save(School.builder().name("경희대학교-6601").build());
        Department chem = departmentRepository.save(Department.builder()
                .school(school).college("공과대학").name("화학공학과").build());
        Department newMat = departmentRepository.save(Department.builder()
                .school(school).college("공과대학").name("신소재공학과").build());
        Division newMatMajorRequired = divisionRepository.save(Division.builder()
                .school(school).code("04").category(DivisionCategory.MAJOR_REQUIRED).build());
        Division recognizedAsElective = divisionRepository.save(Division.builder()
                .school(school).code("05").category(DivisionCategory.MAJOR_ELECTIVE).build());

        // 신소재공학과 기준 전공필수 과목이지만, 화학공학과는 이걸 전공선택으로 인정해줌
        Course crossMajorCourse = courseRepository.save(Course.builder()
                .school(school).courseCode("MAT201").name("신소재공학개론").credit(3)
                .offeringDepartment(newMat).defaultDivision(newMatMajorRequired)
                .openedSemester(OpenedSemester.BOTH).isEnglish(false).isSw(false).build());
        crossMajorRecognizedCourseRepository.save(CrossMajorRecognizedCourse.builder()
                .targetDepartment(chem).course(crossMajorCourse).recognizedDivision(recognizedAsElective).build());

        StudentProfile chemStudent = onboardedStudent("6601", chem);

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
                """.formatted(crossMajorCourse.getId());

        mockMvc.perform(put("/api/v1/planner")
                        .with(authentication(authenticationOf(chemStudent.getMember().getId())))
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PLAN_200_1"));

        PlannerVersionItem savedItem = plannerVersionItemRepository.findAll().stream()
                .filter(item -> item.getCourse().getId().equals(crossMajorCourse.getId()))
                .findFirst().orElseThrow();
        assertThat(savedItem.getPlannedDivision().getId()).isEqualTo(recognizedAsElective.getId());
    }

    @Test
    void 타전공인정_대상이_아니면_과목_자체의_기본_이수구분이_저장된다() throws Exception {
        School school = schoolRepository.save(School.builder().name("경희대학교-6602").build());
        Department cs = departmentRepository.save(Department.builder()
                .school(school).college("공과대학").name("컴퓨터공학과").build());
        Division majorRequired = divisionRepository.save(Division.builder()
                .school(school).code("04").category(DivisionCategory.MAJOR_REQUIRED).build());
        Course course = courseRepository.save(Course.builder()
                .school(school).courseCode("CS201").name("자료구조").credit(3)
                .offeringDepartment(cs).defaultDivision(majorRequired)
                .openedSemester(OpenedSemester.BOTH).isEnglish(false).isSw(false).build());
        StudentProfile student = onboardedStudent("6602", cs);

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
                """.formatted(course.getId());

        mockMvc.perform(put("/api/v1/planner")
                        .with(authentication(authenticationOf(student.getMember().getId())))
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isOk());

        PlannerVersionItem savedItem = plannerVersionItemRepository.findAll().stream()
                .filter(item -> item.getCourse().getId().equals(course.getId()))
                .findFirst().orElseThrow();
        assertThat(savedItem.getPlannedDivision().getId()).isEqualTo(majorRequired.getId());
    }

    @Test
    void 시뮬레이션ID_없이_두번_저장해도_같은_시뮬레이션이_재사용된다() throws Exception {
        School school = schoolRepository.save(School.builder().name("경희대학교-6603").build());
        Department cs = departmentRepository.save(Department.builder()
                .school(school).college("공과대학").name("컴퓨터공학과").build());
        Course course = courseRepository.save(Course.builder()
                .school(school).courseCode("CS202").name("알고리즘").credit(3)
                .offeringDepartment(cs)
                .openedSemester(OpenedSemester.BOTH).isEnglish(false).isSw(false).build());
        StudentProfile student = onboardedStudent("6603", cs);

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
                """.formatted(course.getId());

        Authentication auth = authenticationOf(student.getMember().getId());

        mockMvc.perform(put("/api/v1/planner")
                        .with(authentication(auth))
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/planner")
                        .with(authentication(auth))
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isOk());

        long simulationCount = plannerSimulationRepository.findAll().stream()
                .filter(s -> s.getStudentProfile().getId().equals(student.getId()))
                .count();
        assertThat(simulationCount).isEqualTo(1);
    }

    @Test
    void terms를_빈_배열로_저장하면_기존_학기가_전부_삭제된다() throws Exception {
        School school = schoolRepository.save(School.builder().name("경희대학교-6605").build());
        Department cs = departmentRepository.save(Department.builder()
                .school(school).college("공과대학").name("컴퓨터공학과").build());
        Course course = courseRepository.save(Course.builder()
                .school(school).courseCode("CS203").name("운영체제").credit(3)
                .offeringDepartment(cs)
                .openedSemester(OpenedSemester.BOTH).isEnglish(false).isSw(false).build());
        StudentProfile student = onboardedStudent("6605", cs);

        String saveWithTerm = """
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
                """.formatted(course.getId());

        Authentication auth = authenticationOf(student.getMember().getId());

        mockMvc.perform(put("/api/v1/planner")
                        .with(authentication(auth))
                        .contentType("application/json")
                        .content(saveWithTerm))
                .andExpect(status().isOk());

        Long plannerSimulationId = plannerSimulationRepository.findByStudentProfile(student)
                .orElseThrow().getId();

        String saveEmpty = """
                {
                  "plannerSimulationId": %d,
                  "terms": []
                }
                """.formatted(plannerSimulationId);

        mockMvc.perform(put("/api/v1/planner")
                        .with(authentication(auth))
                        .contentType("application/json")
                        .content(saveEmpty))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/planner")
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.plannedTerms.length()").value(0));
    }

    // 자동저장은 변경이 생길 때마다 전체 PUT을 보낸다. 연속 저장 시 이전 데이터가 완전히 삭제되고
    // 마지막 저장 내용만 남아야 한다. 이전에는 deleteAllByPlannerSimulationId가 다른 트랜잭션이
    // 커밋한 새 PlannerTerm까지 건드려 FK 위반(DataIntegrityViolationException)이 발생했다(#콘커런시픽스).
    @Test
    void 연속_저장_시_이전_데이터가_남지_않고_마지막_저장만_반영된다() throws Exception {
        School school = schoolRepository.save(School.builder().name("경희대학교-6610").build());
        Department cs = departmentRepository.save(Department.builder()
                .school(school).college("공과대학").name("컴퓨터공학과").build());
        Course courseA = courseRepository.save(Course.builder()
                .school(school).courseCode("CS301").name("컴파일러").credit(3)
                .offeringDepartment(cs).openedSemester(OpenedSemester.BOTH)
                .isEnglish(false).isSw(false).build());
        Course courseB = courseRepository.save(Course.builder()
                .school(school).courseCode("CS302").name("컴퓨터네트워크").credit(3)
                .offeringDepartment(cs).openedSemester(OpenedSemester.BOTH)
                .isEnglish(false).isSw(false).build());
        StudentProfile student = onboardedStudent("6610", cs);
        Authentication auth = authenticationOf(student.getMember().getId());

        // 1차 저장: courseA
        mockMvc.perform(put("/api/v1/planner")
                        .with(authentication(auth))
                        .contentType("application/json")
                        .content(singleCoursePlannerBody(null, courseA.getId())))
                .andExpect(status().isOk());

        Long simulationId = plannerSimulationRepository.findByStudentProfile(student)
                .orElseThrow().getId();

        // 2차 저장: courseB로 교체
        mockMvc.perform(put("/api/v1/planner")
                        .with(authentication(auth))
                        .contentType("application/json")
                        .content(singleCoursePlannerBody(simulationId, courseB.getId())))
                .andExpect(status().isOk());

        // courseB만 남아야 하고, courseA의 term/version/item이 잔존하면 안 됨
        mockMvc.perform(get("/api/v1/planner").with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.plannedTerms.length()").value(1))
                .andExpect(jsonPath("$.data.plannedTerms[0].versions[0].courses.length()").value(1))
                .andExpect(jsonPath("$.data.plannedTerms[0].versions[0].courses[0].courseId")
                        .value(courseB.getId()));
    }

    // 자동저장 race condition: 사용자가 빠르게 변경을 연속으로 하면 두 PUT이 겹쳐 들어온다.
    // 비관적 락 + READ_COMMITTED 로 직렬화해서 둘 다 200이고, 한 쪽의 상태만 최종 반영되어야 한다.
    // 두 요청 데이터가 동시에 DB에 남으면(term이 2개) plannedTerms.length = 2 → 회귀 탐지.
    @Test
    void 동시_PUT_요청이_겹쳐도_FK_위반없이_하나의_최종_상태만_남는다() throws Exception {
        School school = schoolRepository.save(School.builder().name("경희대학교-6611").build());
        Department cs = departmentRepository.save(Department.builder()
                .school(school).college("공과대학").name("컴퓨터공학과").build());
        Course courseA = courseRepository.save(Course.builder()
                .school(school).courseCode("CS303").name("소프트웨어공학").credit(3)
                .offeringDepartment(cs).openedSemester(OpenedSemester.BOTH)
                .isEnglish(false).isSw(false).build());
        Course courseB = courseRepository.save(Course.builder()
                .school(school).courseCode("CS304").name("데이터베이스").credit(3)
                .offeringDepartment(cs).openedSemester(OpenedSemester.BOTH)
                .isEnglish(false).isSw(false).build());
        StudentProfile student = onboardedStudent("6611", cs);
        Authentication auth = authenticationOf(student.getMember().getId());

        // 초기 저장: 동시 요청이 "삭제 후 재삽입"할 대상 데이터 생성
        mockMvc.perform(put("/api/v1/planner")
                        .with(authentication(auth))
                        .contentType("application/json")
                        .content(singleCoursePlannerBody(null, courseA.getId())))
                .andExpect(status().isOk());

        Long simulationId = plannerSimulationRepository.findByStudentProfile(student)
                .orElseThrow().getId();

        // 두 PUT을 동시에 발사 — startLatch로 두 스레드가 최대한 같은 시점에 요청을 보내도록 함
        List<Integer> statuses = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Future<?> f1 = executor.submit(() -> {
            try {
                startLatch.await();
                int status = mockMvc.perform(put("/api/v1/planner")
                                .with(authentication(auth))
                                .contentType("application/json")
                                .content(singleCoursePlannerBody(simulationId, courseA.getId())))
                        .andReturn().getResponse().getStatus();
                statuses.add(status);
            } catch (Exception e) {
                statuses.add(500);
            }
        });

        Future<?> f2 = executor.submit(() -> {
            try {
                startLatch.await();
                int status = mockMvc.perform(put("/api/v1/planner")
                                .with(authentication(auth))
                                .contentType("application/json")
                                .content(singleCoursePlannerBody(simulationId, courseB.getId())))
                        .andReturn().getResponse().getStatus();
                statuses.add(status);
            } catch (Exception e) {
                statuses.add(500);
            }
        });

        startLatch.countDown();
        f1.get(10, TimeUnit.SECONDS);
        f2.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        // 두 요청 모두 200 — FK 위반(500)이 없어야 함
        assertThat(statuses).containsOnly(200);

        // 한 학생에 시뮬레이션 1개
        long simCount = plannerSimulationRepository.findAll().stream()
                .filter(s -> s.getStudentProfile().getId().equals(student.getId()))
                .count();
        assertThat(simCount).isEqualTo(1);

        // plannedTerms가 정확히 1개 — 두 요청 데이터가 중복으로 남지 않아야 함
        mockMvc.perform(get("/api/v1/planner").with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.plannedTerms.length()").value(1));
    }

    // 자동저장 첫 번째 요청 race condition: plannerSimulationId=null인 "최초 저장" 두 건이 동시에 들어오면
    // 한 쪽이 INSERT 후 unique 위반으로 500을 내뱉던 버그(#217 레이스).
    // StudentProfile FOR UPDATE로 직렬화하면 둘 다 200이고 시뮬레이션은 1개만 남아야 한다.
    @Test
    void 첫_저장_동시_2건_중_시뮬레이션은_1개만_생성되고_500_없음() throws Exception {
        School school = schoolRepository.save(School.builder().name("경희대학교-6612").build());
        Department cs = departmentRepository.save(Department.builder()
                .school(school).college("공과대학").name("컴퓨터공학과").build());
        Course courseA = courseRepository.save(Course.builder()
                .school(school).courseCode("CS401").name("인공지능").credit(3)
                .offeringDepartment(cs).openedSemester(OpenedSemester.BOTH)
                .isEnglish(false).isSw(false).build());
        Course courseB = courseRepository.save(Course.builder()
                .school(school).courseCode("CS402").name("딥러닝").credit(3)
                .offeringDepartment(cs).openedSemester(OpenedSemester.BOTH)
                .isEnglish(false).isSw(false).build());
        StudentProfile student = onboardedStudent("6612", cs);
        Authentication auth = authenticationOf(student.getMember().getId());

        // PlannerSimulation이 없는 상태에서 두 PUT을 동시에 발사 — 둘 다 plannerSimulationId=null
        List<Integer> statuses = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Future<?> f1 = executor.submit(() -> {
            try {
                startLatch.await();
                int status = mockMvc.perform(put("/api/v1/planner")
                                .with(authentication(auth))
                                .contentType("application/json")
                                .content(singleCoursePlannerBody(null, courseA.getId())))
                        .andReturn().getResponse().getStatus();
                statuses.add(status);
            } catch (Exception e) {
                statuses.add(500);
            }
        });

        Future<?> f2 = executor.submit(() -> {
            try {
                startLatch.await();
                int status = mockMvc.perform(put("/api/v1/planner")
                                .with(authentication(auth))
                                .contentType("application/json")
                                .content(singleCoursePlannerBody(null, courseB.getId())))
                        .andReturn().getResponse().getStatus();
                statuses.add(status);
            } catch (Exception e) {
                statuses.add(500);
            }
        });

        startLatch.countDown();
        f1.get(10, TimeUnit.SECONDS);
        f2.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        // 500(DataIntegrityViolationException) 없이 둘 다 200
        assertThat(statuses).containsOnly(200);

        // 시뮬레이션은 정확히 1개
        long simCount = plannerSimulationRepository.findAll().stream()
                .filter(s -> s.getStudentProfile().getId().equals(student.getId()))
                .count();
        assertThat(simCount).isEqualTo(1);

        // 두 저장 중 나중에 커밋된 한 건의 데이터만 남아 plannedTerms가 정확히 1개
        mockMvc.perform(get("/api/v1/planner").with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.plannedTerms.length()").value(1));
    }

    @Test
    void semester_3과_4로_저장하면_성공한다() throws Exception {
        School school = schoolRepository.save(School.builder().name("경희대학교-6614").build());
        Department cs = departmentRepository.save(Department.builder()
                .school(school).college("공과대학").name("컴퓨터공학과").build());
        StudentProfile student = onboardedStudent("6614", cs);

        String requestBody = """
                {
                  "plannerSimulationId": null,
                  "terms": [
                    {
                      "yearLevel": 1,
                      "semester": 3,
                      "versions": [
                        {
                          "versionNo": 1,
                          "name": "여름 계획",
                          "isSelected": true,
                          "versionOrder": 0,
                          "items": []
                        }
                      ]
                    },
                    {
                      "yearLevel": 1,
                      "semester": 4,
                      "versions": [
                        {
                          "versionNo": 1,
                          "name": "겨울 계획",
                          "isSelected": true,
                          "versionOrder": 0,
                          "items": []
                        }
                      ]
                    }
                  ]
                }
                """;

        mockMvc.perform(put("/api/v1/planner")
                        .with(authentication(authenticationOf(student.getMember().getId())))
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PLAN_200_1"));
    }

    @Test
    void semester가_4를_초과하면_400을_반환한다() throws Exception {
        School school = schoolRepository.save(School.builder().name("경희대학교-6615").build());
        Department cs = departmentRepository.save(Department.builder()
                .school(school).college("공과대학").name("컴퓨터공학과").build());
        StudentProfile student = onboardedStudent("6615", cs);

        String requestBody = """
                {
                  "plannerSimulationId": null,
                  "terms": [
                    {
                      "yearLevel": 1,
                      "semester": 5,
                      "versions": [
                        {
                          "versionNo": 1,
                          "isSelected": true,
                          "versionOrder": 0,
                          "items": []
                        }
                      ]
                    }
                  ]
                }
                """;

        mockMvc.perform(put("/api/v1/planner")
                        .with(authentication(authenticationOf(student.getMember().getId())))
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 같은_학기_내_versionOrder가_중복되면_400을_반환한다() throws Exception {
        School school = schoolRepository.save(School.builder().name("경희대학교-6604").build());
        Department cs = departmentRepository.save(Department.builder()
                .school(school).college("공과대학").name("컴퓨터공학과").build());
        StudentProfile student = onboardedStudent("6604", cs);

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
                          "items": []
                        },
                        {
                          "versionNo": 2,
                          "name": "폴더 2",
                          "isSelected": false,
                          "versionOrder": 0,
                          "items": []
                        }
                      ]
                    }
                  ]
                }
                """;

        mockMvc.perform(put("/api/v1/planner")
                        .with(authentication(authenticationOf(student.getMember().getId())))
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLAN_004"));
    }
}
