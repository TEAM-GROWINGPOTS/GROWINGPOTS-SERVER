package com.growingpots.domain.graduation.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

    private Authentication authenticationOf(Long memberId) {
        return new UsernamePasswordAuthenticationToken(memberId.toString(), null, Collections.emptyList());
    }

    // 스포츠의학과 학생 하나를 만들고, 전문실기1~6(2학점씩) + 맨손체조(1학점) 요건과 과목을 세팅한다.
    // 요건: "전문실기 4학점 이상"(minCredit=4), "맨손체조"(minCount=1)
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
                .department(department).division(null).name("졸업필수(전문실기 2과목만 인정)")
                .baseYear(2019).minCredit(4).minCount(0).build());
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
                        .param("majorType", "PRIMARY")
                        .with(authentication(authenticationOf(profile.getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.majors[0].satisfied").value(true))
                .andExpect(jsonPath("$.data.majors[0].hasRequiredList").value(true))
                .andExpect(jsonPath("$.data.majors[0].unmetDescriptions.length()").value(0))
                .andExpect(jsonPath("$.data.majors[0].courses.length()").value(7))
                .andExpect(jsonPath("$.data.majors[0].courses[?(@.name=='전문실기1')].taken").value(true))
                .andExpect(jsonPath("$.data.majors[0].courses[?(@.name=='맨손체조')].taken").value(true));

        mockMvc.perform(get("/api/v1/students/me/graduation")
                        .param("majorType", "PRIMARY")
                        .with(authentication(authenticationOf(profile.getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.graduatable").value(true))
                .andExpect(jsonPath("$.data.graduationRequired.satisfied").value(true))
                .andExpect(jsonPath("$.data.graduationRequired.totalCredit").value(5));
    }

    @Test
    void 전문실기_1과목만_이수하고_맨손체조_미이수면_졸업필수를_만족하지_못한다() throws Exception {
        StudentProfile profile = setUpSportsScienceStudent("9202");
        completeCourse(profile, "CPE201", 2);

        mockMvc.perform(get("/api/v1/students/me/graduation/GRADUATION_REQUIRED/courses")
                        .param("majorType", "PRIMARY")
                        .with(authentication(authenticationOf(profile.getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.majors[0].satisfied").value(false))
                .andExpect(jsonPath("$.data.majors[0].unmetDescriptions.length()").value(1))
                .andExpect(jsonPath("$.data.majors[0].unmetDescriptions[0]")
                        .value("[졸업필수(전문실기 2과목만 인정)] 2/4학점 이수완료"))
                .andExpect(jsonPath("$.data.majors[0].courses[?(@.name=='전문실기1')].taken").value(true))
                .andExpect(jsonPath("$.data.majors[0].courses[?(@.name=='전문실기2')].taken").value(false))
                .andExpect(jsonPath("$.data.majors[0].courses[?(@.name=='맨손체조')].taken").value(false));

        mockMvc.perform(get("/api/v1/students/me/graduation")
                        .param("majorType", "PRIMARY")
                        .with(authentication(authenticationOf(profile.getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.graduatable").value(false))
                .andExpect(jsonPath("$.data.graduationRequired.satisfied").value(false))
                .andExpect(jsonPath("$.data.graduationRequired.totalCredit").value(2))
                .andExpect(jsonPath("$.data.graduationRequired.unmetDescriptions.length()").value(1));
    }

    // 학점 기준 조건(전문실기)은 만족했지만 과목수 기준 조건(맨손체조)만 미충족인 경우.
    // unmetDescriptions는 학점 조건만 담아서 비어있어도 current(만족한 조건 수)는 그대로
    // 반영돼 satisfied=false와 모순되지 않아야 한다(1/2, unmetDescriptions는 빈 리스트).
    @Test
    void 전문실기는_만족하고_맨손체조만_미이수면_current_required가_만족여부와_모순되지_않는다() throws Exception {
        StudentProfile profile = setUpSportsScienceStudent("9204");
        completeCourse(profile, "CPE201", 2);
        completeCourse(profile, "CPE202", 2);

        mockMvc.perform(get("/api/v1/students/me/graduation/GRADUATION_REQUIRED/courses")
                        .param("majorType", "PRIMARY")
                        .with(authentication(authenticationOf(profile.getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.majors[0].satisfied").value(false))
                .andExpect(jsonPath("$.data.majors[0].current").value(1))
                .andExpect(jsonPath("$.data.majors[0].required").value(2))
                .andExpect(jsonPath("$.data.majors[0].unmetDescriptions.length()").value(0));

        mockMvc.perform(get("/api/v1/students/me/graduation")
                        .param("majorType", "PRIMARY")
                        .with(authentication(authenticationOf(profile.getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.graduatable").value(false))
                .andExpect(jsonPath("$.data.graduationRequired.satisfied").value(false))
                .andExpect(jsonPath("$.data.graduationRequired.unmetDescriptions.length()").value(0));
    }

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
                        .param("majorType", "PRIMARY")
                        .with(authentication(authenticationOf(profile.getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.majors[0].hasRequiredList").value(false))
                .andExpect(jsonPath("$.data.majors[0].courses.length()").value(0));

        mockMvc.perform(get("/api/v1/students/me/graduation")
                        .param("majorType", "PRIMARY")
                        .with(authentication(authenticationOf(profile.getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.graduationRequired").doesNotExist());
    }
}
