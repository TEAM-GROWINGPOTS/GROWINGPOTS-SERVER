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
import com.growingpots.domain.university.entity.Division;
import com.growingpots.domain.university.entity.School;
import com.growingpots.domain.university.entity.enums.DivisionCategory;
import com.growingpots.domain.university.repository.CourseRepository;
import com.growingpots.domain.university.repository.DepartmentRepository;
import com.growingpots.domain.university.repository.DivisionRepository;
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

// 영어강의/SW인증 "이수구분별 과목 목록" 상세 조회(GET /graduation/{code}/courses)가 스냅샷 숫자(current)와
// 최대한 격차 없이 동작하는지 검증한다: 재수강 중인 과목이 중복으로 안 나오고, 진행중(IN_PROGRESS)인
// 과목도 목록에 포함되고, 이수구분이 전공 카테고리가 아니어도(자유이수 등) 본전공 조회에서 빠지지 않아야 한다.
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class GraduationEnglishSwCourseListTest {

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
    private DivisionRepository divisionRepository;

    @Autowired
    private StudentCourseRepository studentCourseRepository;

    private Authentication authenticationOf(Long memberId) {
        return new UsernamePasswordAuthenticationToken(memberId.toString(), null, Collections.emptyList());
    }

    @Test
    void 재수강중인_과목은_중복없이_한번만_나오고_진행중_과목도_목록에_포함되며_교양이수구분도_전공조회에서_빠지지_않는다() throws Exception {
        School school = schoolRepository.save(School.builder().name("경희대학교-9401").build());
        Department department = departmentRepository.save(Department.builder()
                .school(school).college("체육대학").name("스포츠의학과-9401").build());
        Member member = memberRepository.save(Member.builder()
                .nickname("테스트유저").oauthProvider(OauthProvider.KAKAO).oauthId("9401").email(null).build());
        StudentProfile profile = studentProfileRepository.save(StudentProfile.builder()
                .member(member).school(school).department(department).admissionYear(2023).build());
        StudentMajor main = studentMajorRepository.save(StudentMajor.builder()
                .studentProfile(profile).department(department).majorType(MajorType.MAIN).build());
        graduationAnalysisSummaryRepository.save(GraduationAnalysisSummary.builder()
                .studentMajor(main)
                .englishCurrent(7).englishRequired(3)
                .swCertCurrent(6).swCertRequired(6)
                .build());

        Division majorRequired = divisionRepository.save(Division.builder()
                .school(school).code("04").category(DivisionCategory.MAJOR_REQUIRED).build());
        Division freeGe = divisionRepository.save(Division.builder()
                .school(school).code("06").category(DivisionCategory.FREE_GE).build());

        // 재수강 중: 과거 완료 이력(2024/1) + 현재 진행중 이력(2026/1) 두 행이 같은 과목코드로 존재.
        Course retakeCourse = courseRepository.save(Course.builder()
                .school(school).courseCode("SM2011").name("운동손상").credit(3)
                .isEnglish(true).isSw(false).isActive(true).build());
        studentCourseRepository.save(StudentCourse.builder()
                .studentProfile(profile).course(retakeCourse).appliedDivision(majorRequired)
                .rawCourseCode("SM2011").rawCourseName("운동손상").credit(3)
                .takenYear(2024).takenSemester(Semester.FIRST)
                .status(CourseStatus.COMPLETED).source(RecordSource.PDF).isRetake(true).build());
        studentCourseRepository.save(StudentCourse.builder()
                .studentProfile(profile).course(retakeCourse).appliedDivision(majorRequired)
                .rawCourseCode("SM2011").rawCourseName("운동손상").credit(3)
                .takenYear(2026).takenSemester(Semester.FIRST)
                .status(CourseStatus.IN_PROGRESS).source(RecordSource.PDF).isRetake(false).build());

        // 완료 이력 없이 진행중(현재 학기 수강신청)만 있는 과목도 목록에 포함돼야 한다.
        Course inProgressOnlyCourse = courseRepository.save(Course.builder()
                .school(school).courseCode("SM328").name("임상역학").credit(3)
                .isEnglish(true).isSw(false).isActive(true).build());
        studentCourseRepository.save(StudentCourse.builder()
                .studentProfile(profile).course(inProgressOnlyCourse).appliedDivision(majorRequired)
                .rawCourseCode("SM328").rawCourseName("임상역학").credit(3)
                .takenYear(2026).takenSemester(Semester.FIRST)
                .status(CourseStatus.IN_PROGRESS).source(RecordSource.PDF).isRetake(false).build());

        // SW 과목이 전공이 아닌 자유이수(교양)에 있어도 본전공 조회 시 목록에서 빠지면 안 된다.
        Course swInFreeGe = courseRepository.save(Course.builder()
                .school(school).courseCode("GEE1979").name("인터넷과메타버스").credit(3)
                .isEnglish(false).isSw(true).isActive(true).build());
        studentCourseRepository.save(StudentCourse.builder()
                .studentProfile(profile).course(swInFreeGe).appliedDivision(freeGe)
                .rawCourseCode("GEE1979").rawCourseName("인터넷과메타버스").credit(3)
                .takenYear(2024).takenSemester(Semester.FIRST)
                .status(CourseStatus.COMPLETED).source(RecordSource.PDF).isRetake(false).build());

        mockMvc.perform(get("/api/v1/students/me/graduation/ENGLISH_COURSE/courses")
                        .param("studentMajorId", String.valueOf(main.getId()))
                        .with(authentication(authenticationOf(profile.getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.majors[0].current").value(7))
                .andExpect(jsonPath("$.data.majors[0].courses.length()").value(2))
                .andExpect(jsonPath("$.data.majors[0].courses[?(@.name=='운동손상')].taken").value(true))
                .andExpect(jsonPath("$.data.majors[0].courses[?(@.name=='임상역학')].taken").value(true));

        mockMvc.perform(get("/api/v1/students/me/graduation/SW_CERT_COURSE/courses")
                        .param("studentMajorId", String.valueOf(main.getId()))
                        .with(authentication(authenticationOf(profile.getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.majors[0].current").value(6))
                .andExpect(jsonPath("$.data.majors[0].courses.length()").value(1))
                .andExpect(jsonPath("$.data.majors[0].courses[0].name").value("인터넷과메타버스"));
    }
}
