package com.growingpots.domain.planner.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
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
}
