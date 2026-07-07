package com.growingpots.domain.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.growingpots.domain.transcript.entity.StudentCourse;
import com.growingpots.domain.transcript.entity.enums.CourseStatus;
import com.growingpots.domain.transcript.entity.enums.RecordSource;
import com.growingpots.domain.transcript.entity.enums.Semester;
import com.growingpots.domain.transcript.repository.StudentCourseRepository;
import com.growingpots.domain.university.entity.Department;
import com.growingpots.domain.university.entity.Division;
import com.growingpots.domain.university.entity.School;
import com.growingpots.domain.university.entity.enums.DivisionCategory;
import com.growingpots.domain.university.repository.DepartmentRepository;
import com.growingpots.domain.university.repository.DivisionRepository;
import com.growingpots.domain.university.repository.SchoolRepository;
import com.growingpots.domain.user.entity.Member;
import com.growingpots.domain.user.entity.StudentProfile;
import com.growingpots.domain.user.entity.enums.OauthProvider;
import com.growingpots.domain.user.repository.MemberRepository;
import com.growingpots.domain.user.repository.StudentProfileRepository;
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

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class StudentCourseUpdateTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
    private StudentCourseRepository studentCourseRepository;

    private StudentProfile onboardedStudent(String oauthId) {
        Member member = memberRepository.save(Member.builder()
                .nickname("테스트유저")
                .oauthProvider(OauthProvider.KAKAO)
                .oauthId(oauthId)
                .email(null)
                .build());
        School school = schoolRepository.save(School.builder().name("경희대학교-" + oauthId).build());
        Department department = departmentRepository.save(Department.builder()
                .school(school)
                .college("공과대학")
                .name("컴퓨터공학과")
                .build());
        return studentProfileRepository.save(StudentProfile.builder()
                .member(member)
                .school(school)
                .department(department)
                .admissionYear(2023)
                .build());
    }

    private Authentication authenticationOf(Long memberId) {
        return new UsernamePasswordAuthenticationToken(memberId.toString(), null, Collections.emptyList());
    }

    @Test
    void 수정_추가_삭제가_한번의_PUT으로_모두_반영된다() throws Exception {
        StudentProfile studentProfile = onboardedStudent("3601");
        School school = studentProfile.getSchool();
        Department department = departmentRepository.save(Department.builder()
                .school(school)
                .college("예술·디자인대학")
                .name("연극영화학과")
                .build());
        Division division = divisionRepository.save(Division.builder()
                .school(school)
                .code("05")
                .category(DivisionCategory.MAJOR_ELECTIVE)
                .build());
        Division generalElective = divisionRepository.save(Division.builder()
                .school(school)
                .code("08")
                .category(DivisionCategory.GENERAL_ELECTIVE)
                .build());

        StudentCourse toUpdate = studentCourseRepository.save(StudentCourse.builder()
                .studentProfile(studentProfile)
                .rawCourseCode("THE2001")
                .rawCourseName("연극문헌과연기")
                .credit(3)
                .takenYear(2023)
                .takenSemester(Semester.FIRST)
                .status(CourseStatus.COMPLETED)
                .source(RecordSource.PDF)
                .build());
        StudentCourse toDelete = studentCourseRepository.save(StudentCourse.builder()
                .studentProfile(studentProfile)
                .rawCourseCode("OLD001")
                .rawCourseName("삭제될 과목")
                .credit(2)
                .takenYear(2022)
                .takenSemester(Semester.SECOND)
                .status(CourseStatus.COMPLETED)
                .source(RecordSource.PDF)
                .build());

        String requestBody = """
                {
                  "courses": [
                    {
                      "studentCourseId": %d,
                      "courseId": null,
                      "rawCourseName": "연극문헌과연기(수정)",
                      "departmentId": %d,
                      "credit": 4,
                      "appliedDivisionId": %d,
                      "takenYear": 2024,
                      "takenSemester": "SECOND"
                    },
                    {
                      "studentCourseId": null,
                      "courseId": null,
                      "rawCourseName": "직접추가한교양",
                      "departmentId": null,
                      "credit": 2,
                      "appliedDivisionId": %d,
                      "takenYear": 2023,
                      "takenSemester": "SUMMER"
                    }
                  ]
                }
                """.formatted(toUpdate.getId(), department.getId(), division.getId(), generalElective.getId());

        mockMvc.perform(put("/api/v1/students/me/courses")
                        .with(authentication(authenticationOf(studentProfile.getMember().getId())))
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("USER_200_4"))
                .andExpect(jsonPath("$.data").doesNotExist());

        List<StudentCourse> remaining = studentCourseRepository.findByStudentProfile(studentProfile);
        assertThat(remaining).hasSize(2);
        assertThat(remaining).noneMatch(sc -> sc.getId().equals(toDelete.getId()));

        StudentCourse updated = studentCourseRepository.findById(toUpdate.getId()).orElseThrow();
        assertThat(updated.getRawCourseName()).isEqualTo("연극문헌과연기(수정)");
        assertThat(updated.getCredit()).isEqualTo(4);
        assertThat(updated.getAppliedDepartment().getId()).isEqualTo(department.getId());
        assertThat(updated.getAppliedDivision().getId()).isEqualTo(division.getId());
        assertThat(updated.getTakenYear()).isEqualTo(2024);
        assertThat(updated.getTakenSemester()).isEqualTo(Semester.SECOND);

        StudentCourse created = remaining.stream()
                .filter(sc -> !sc.getId().equals(toUpdate.getId()))
                .findFirst().orElseThrow();
        assertThat(created.getRawCourseName()).isEqualTo("직접추가한교양");
        assertThat(created.getSource()).isEqualTo(RecordSource.MANUAL);
        assertThat(created.getStatus()).isEqualTo(CourseStatus.COMPLETED);
        assertThat(created.getAppliedDivision().getId()).isEqualTo(generalElective.getId());
        assertThat(created.getTakenSemester()).isEqualTo(Semester.SUMMER);
    }

    @Test
    void 다른_학생의_studentCourseId를_보내면_400_CMN_002를_반환한다() throws Exception {
        StudentProfile me = onboardedStudent("3602");
        StudentProfile other = onboardedStudent("3603");
        StudentCourse othersCourse = studentCourseRepository.save(StudentCourse.builder()
                .studentProfile(other)
                .rawCourseCode("OTH001")
                .rawCourseName("남의 과목")
                .credit(3)
                .status(CourseStatus.COMPLETED)
                .source(RecordSource.PDF)
                .build());

        String requestBody = """
                {
                  "courses": [
                    {
                      "studentCourseId": %d,
                      "courseId": null,
                      "rawCourseName": "탈취 시도",
                      "departmentId": null,
                      "credit": 3,
                      "appliedDivisionId": null,
                      "takenYear": 2024,
                      "takenSemester": "FIRST"
                    }
                  ]
                }
                """.formatted(othersCourse.getId());

        mockMvc.perform(put("/api/v1/students/me/courses")
                        .with(authentication(authenticationOf(me.getMember().getId())))
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN_002"));
    }

    @Test
    void 존재하지_않는_appliedDivisionId면_400_CMN_002를_반환한다() throws Exception {
        StudentProfile studentProfile = onboardedStudent("3604");

        String requestBody = """
                {
                  "courses": [
                    {
                      "studentCourseId": null,
                      "courseId": null,
                      "rawCourseName": "직접추가",
                      "departmentId": null,
                      "credit": 2,
                      "appliedDivisionId": 999999,
                      "takenYear": 2024,
                      "takenSemester": "FIRST"
                    }
                  ]
                }
                """;

        mockMvc.perform(put("/api/v1/students/me/courses")
                        .with(authentication(authenticationOf(studentProfile.getMember().getId())))
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN_002"));
    }

    @Test
    void 다른_학교_소속의_department_division_courseId면_400_CMN_002를_반환한다() throws Exception {
        StudentProfile studentProfile = onboardedStudent("3606");
        School otherSchool = schoolRepository.save(School.builder().name("다른학교-3606").build());
        Department otherDepartment = departmentRepository.save(Department.builder()
                .school(otherSchool)
                .college("타학교단과대")
                .name("타학교학과")
                .build());
        Division otherDivision = divisionRepository.save(Division.builder()
                .school(otherSchool)
                .code("04")
                .category(DivisionCategory.MAJOR_REQUIRED)
                .build());

        String requestBody = """
                {
                  "courses": [
                    {
                      "studentCourseId": null,
                      "courseId": null,
                      "rawCourseName": "직접추가",
                      "departmentId": %d,
                      "credit": 2,
                      "appliedDivisionId": %d,
                      "takenYear": 2024,
                      "takenSemester": "FIRST"
                    }
                  ]
                }
                """.formatted(otherDepartment.getId(), otherDivision.getId());

        mockMvc.perform(put("/api/v1/students/me/courses")
                        .with(authentication(authenticationOf(studentProfile.getMember().getId())))
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN_002"));
    }

    @Test
    void 온보딩_전이면_404_USER_003을_반환한다() throws Exception {
        Member member = memberRepository.save(Member.builder()
                .nickname("온보딩안한유저")
                .oauthProvider(OauthProvider.KAKAO)
                .oauthId("3605")
                .email(null)
                .build());

        mockMvc.perform(put("/api/v1/students/me/courses")
                        .with(authentication(authenticationOf(member.getId())))
                        .contentType("application/json")
                        .content(SINGLE_MANUAL_COURSE_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_003"));
    }

    @Test
    void 인증_헤더가_없으면_401_CMN_005를_반환한다() throws Exception {
        mockMvc.perform(put("/api/v1/students/me/courses")
                        .contentType("application/json")
                        .content(SINGLE_MANUAL_COURSE_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("CMN_005"));
    }

    private static final String SINGLE_MANUAL_COURSE_BODY = """
            {
              "courses": [
                {
                  "studentCourseId": null,
                  "courseId": null,
                  "rawCourseName": "직접추가",
                  "departmentId": null,
                  "credit": 2,
                  "appliedDivisionId": null,
                  "takenYear": 2024,
                  "takenSemester": "FIRST"
                }
              ]
            }
            """;
}
