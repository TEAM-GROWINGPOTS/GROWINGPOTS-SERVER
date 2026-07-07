package com.growingpots.domain.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class StudentCourseListTest {

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
    private CourseRepository courseRepository;

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

    private Division division(School school, String code, DivisionCategory category) {
        return divisionRepository.save(Division.builder()
                .school(school)
                .code(code)
                .category(category)
                .build());
    }

    private Authentication authenticationOf(Long memberId) {
        return new UsernamePasswordAuthenticationToken(memberId.toString(), null, Collections.emptyList());
    }

    @Test
    void 이수과목_목록을_조회하면_교양은_이수구분명이_전공은_null이_나온다() throws Exception {
        StudentProfile studentProfile = onboardedStudent("1001");
        School school = studentProfile.getSchool();
        Division freeGe = division(school, "02", DivisionCategory.GE_FREE);

        studentCourseRepository.save(StudentCourse.builder()
                .studentProfile(studentProfile)
                .appliedDivision(freeGe)
                .rawCourseCode("GEC1104")
                .rawCourseName("World Citizen")
                .credit(3)
                .takenYear(2023)
                .takenSemester(Semester.FIRST)
                .status(CourseStatus.COMPLETED)
                .source(RecordSource.PDF)
                .build());
        studentCourseRepository.save(StudentCourse.builder()
                .studentProfile(studentProfile)
                .rawCourseCode("THE2001")
                .rawCourseName("연극문헌과연기")
                .credit(3)
                .takenYear(2023)
                .takenSemester(Semester.SECOND)
                .status(CourseStatus.COMPLETED)
                .source(RecordSource.PDF)
                .build());

        String responseBody = mockMvc.perform(get("/api/v1/students/me/courses")
                        .with(authentication(authenticationOf(studentProfile.getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("USER_200_3"))
                .andExpect(jsonPath("$.data.courses.length()").value(2))
                .andReturn().getResponse().getContentAsString();

        JsonNode courses = objectMapper.readTree(responseBody).path("data").path("courses");
        assertThat(departmentNameOf(courses, "GEC1104")).isEqualTo("교양");
        assertThat(departmentNameOf(courses, "THE2001")).isNull();
    }

    private String departmentNameOf(JsonNode courses, String courseCode) {
        for (JsonNode course : courses) {
            if (courseCode.equals(course.path("courseCode").asText())) {
                JsonNode value = course.path("departmentName");
                return value.isNull() ? null : value.asText();
            }
        }
        throw new AssertionError("과목을 찾을 수 없음: " + courseCode);
    }

    @Test
    void COURSE_마스터와_매칭되면_departmentName이_채워진다() throws Exception {
        StudentProfile studentProfile = onboardedStudent("2002");
        School school = studentProfile.getSchool();
        Department offeringDepartment = departmentRepository.save(Department.builder()
                .school(school)
                .college("예술·디자인대학")
                .name("연극영화학과")
                .build());
        Course course = courseRepository.save(Course.builder()
                .school(school)
                .courseCode("THE2001")
                .name("연극문헌과연기")
                .credit(3)
                .offeringDepartment(offeringDepartment)
                .build());
        Division majorRequired = division(school, "04", DivisionCategory.MAJOR_REQUIRED);
        studentCourseRepository.save(StudentCourse.builder()
                .studentProfile(studentProfile)
                .course(course)
                .appliedDivision(majorRequired)
                .rawCourseCode("THE2001")
                .rawCourseName("연극문헌과연기")
                .credit(3)
                .takenYear(2023)
                .takenSemester(Semester.FIRST)
                .rawClassification("04")
                .status(CourseStatus.COMPLETED)
                .source(RecordSource.PDF)
                .build());

        mockMvc.perform(get("/api/v1/students/me/courses")
                        .with(authentication(authenticationOf(studentProfile.getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.courses[0].departmentName").value("연극영화학과"))
                .andExpect(jsonPath("$.data.courses[0].takenSemester").value("1학기"))
                .andExpect(jsonPath("$.data.courses[0].appliedDivisionName").value("전공필수"));
    }

    @Test
    void appliedDivision_카테고리로_이수구분명이_매핑된다() throws Exception {
        StudentProfile studentProfile = onboardedStudent("4004");
        School school = studentProfile.getSchool();
        Division majorElective = division(school, "05", DivisionCategory.MAJOR_ELECTIVE);
        Division majorBasic = division(school, "11", DivisionCategory.MAJOR_BASIC);

        studentCourseRepository.save(StudentCourse.builder()
                .studentProfile(studentProfile)
                .appliedDivision(majorElective)
                .rawCourseCode("FT2076")
                .rawCourseName("초급영화이론")
                .credit(3)
                .takenYear(2024)
                .takenSemester(Semester.FIRST)
                .rawClassification("05")
                .status(CourseStatus.COMPLETED)
                .source(RecordSource.PDF)
                .build());
        studentCourseRepository.save(StudentCourse.builder()
                .studentProfile(studentProfile)
                .appliedDivision(majorBasic)
                .rawCourseCode("FT1003")
                .rawCourseName("영화사")
                .credit(3)
                .takenYear(2023)
                .takenSemester(Semester.FIRST)
                .rawClassification("11")
                .status(CourseStatus.COMPLETED)
                .source(RecordSource.PDF)
                .build());
        // COURSE 미매칭 + appliedDivision도 없는 전공 과목(예: 시드 누락된 타전공 과목)은 null로 남아야 한다.
        studentCourseRepository.save(StudentCourse.builder()
                .studentProfile(studentProfile)
                .rawCourseCode("FR2042")
                .rawCourseName("프랑스영화예술")
                .credit(3)
                .takenYear(2024)
                .takenSemester(Semester.SECOND)
                .rawClassification(null)
                .status(CourseStatus.COMPLETED)
                .source(RecordSource.PDF)
                .build());

        String responseBody = mockMvc.perform(get("/api/v1/students/me/courses")
                        .with(authentication(authenticationOf(studentProfile.getMember().getId()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode courses = objectMapper.readTree(responseBody).path("data").path("courses");
        assertThat(appliedDivisionNameOf(courses, "FT2076")).isEqualTo("전공선택");
        assertThat(appliedDivisionNameOf(courses, "FT1003")).isEqualTo("전공기초");
        assertThat(appliedDivisionNameOf(courses, "FR2042")).isNull();
    }

    @Test
    void appliedDivision이_GENERAL_ELECTIVE면_교양으로_분류된다() throws Exception {
        StudentProfile studentProfile = onboardedStudent("5005");
        School school = studentProfile.getSchool();
        Division generalElective = division(school, "08", DivisionCategory.GENERAL_ELECTIVE);
        // 금학기수강학점 구역의 "08 11"처럼 파서가 원래 코드를 온전히 못 잡아도, 결국 GENERAL_ELECTIVE로
        // 해석되면(TranscriptPersister.resolveAppliedDivision) 교양으로 표시돼야 한다.
        studentCourseRepository.save(StudentCourse.builder()
                .studentProfile(studentProfile)
                .appliedDivision(generalElective)
                .rawCourseCode("BME213")
                .rawCourseName("기초프로그래밍")
                .credit(3)
                .rawClassification("0811")
                .status(CourseStatus.IN_PROGRESS)
                .source(RecordSource.PDF)
                .build());

        String responseBody = mockMvc.perform(get("/api/v1/students/me/courses")
                        .with(authentication(authenticationOf(studentProfile.getMember().getId()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode courses = objectMapper.readTree(responseBody).path("data").path("courses");
        assertThat(appliedDivisionNameOf(courses, "BME213")).isEqualTo("일반선택");
        assertThat(departmentNameOf(courses, "BME213")).isEqualTo("교양");
    }

    private String appliedDivisionNameOf(JsonNode courses, String courseCode) {
        for (JsonNode course : courses) {
            if (courseCode.equals(course.path("courseCode").asText())) {
                JsonNode value = course.path("appliedDivisionName");
                return value.isNull() ? null : value.asText();
            }
        }
        throw new AssertionError("과목을 찾을 수 없음: " + courseCode);
    }

    @Test
    void 온보딩_전이면_404_USER_003을_반환한다() throws Exception {
        Member member = memberRepository.save(Member.builder()
                .nickname("온보딩안한유저")
                .oauthProvider(OauthProvider.KAKAO)
                .oauthId("3003")
                .email(null)
                .build());

        mockMvc.perform(get("/api/v1/students/me/courses")
                        .with(authentication(authenticationOf(member.getId()))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_003"));
    }

    @Test
    void 인증_헤더가_없으면_401_CMN_005를_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/students/me/courses"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("CMN_005"));
    }
}
