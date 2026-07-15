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

// 재수강 중인 과목(과거 완료 이력 + 현재 진행중 이력, 두 개의 StudentCourse 행)이 일반 이수구분
// (MAJOR_REQUIRED 등) "이수 과목" 목록 조회에서 중복으로 뜨지 않아야 한다(#198). 영어/SW 목록에서
// 먼저 발견해 dedupeByCourse로 고쳤던 것과 동일한 근본 원인이 일반 이수구분 목록에도 있었다.
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class GraduationCourseListDedupTest {

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
    void 재수강중인_과목은_전공필수_이수과목_목록에서_중복으로_안뜨고_완료행이_우선노출된다() throws Exception {
        School school = schoolRepository.save(School.builder().name("경희대학교-9501").build());
        Department department = departmentRepository.save(Department.builder()
                .school(school).college("체육대학").name("스포츠의학과-9501").build());
        Member member = memberRepository.save(Member.builder()
                .nickname("테스트유저").oauthProvider(OauthProvider.KAKAO).oauthId("9501").email(null).build());
        StudentProfile profile = studentProfileRepository.save(StudentProfile.builder()
                .member(member).school(school).department(department).admissionYear(2023).build());
        StudentMajor main = studentMajorRepository.save(StudentMajor.builder()
                .studentProfile(profile).department(department).majorType(MajorType.MAIN).build());
        graduationAnalysisSummaryRepository.save(GraduationAnalysisSummary.builder()
                .studentMajor(main)
                .majorRequiredCurrent(9).majorRequiredRequired(9)
                .build());

        Division majorRequired = divisionRepository.save(Division.builder()
                .school(school).code("04").category(DivisionCategory.MAJOR_REQUIRED).build());
        Course retakeCourse = courseRepository.save(Course.builder()
                .school(school).courseCode("SM2011").name("운동손상").credit(3)
                .isEnglish(true).isSw(false).isActive(true).build());

        // 과거 재수강 완료 이력
        studentCourseRepository.save(StudentCourse.builder()
                .studentProfile(profile).course(retakeCourse).appliedDivision(majorRequired)
                .rawCourseCode("SM2011").rawCourseName("운동손상").credit(3)
                .takenYear(2024).takenSemester(Semester.FIRST)
                .status(CourseStatus.COMPLETED).source(RecordSource.PDF).isRetake(true).build());
        // 현재 또 재수강 중인 이력(같은 과목, 다른 행)
        studentCourseRepository.save(StudentCourse.builder()
                .studentProfile(profile).course(retakeCourse).appliedDivision(majorRequired)
                .rawCourseCode("SM2011").rawCourseName("운동손상").credit(3)
                .takenYear(2026).takenSemester(Semester.FIRST)
                .status(CourseStatus.IN_PROGRESS).source(RecordSource.PDF).isRetake(false).build());

        // 다른 전공필수 과목 하나(중복 아님) - dedup이 서로 다른 과목까지 잘못 합치지 않는지 확인용
        Course otherCourse = courseRepository.save(Course.builder()
                .school(school).courseCode("SM208").name("기능해부학").credit(3)
                .isEnglish(true).isSw(false).isActive(true).build());
        studentCourseRepository.save(StudentCourse.builder()
                .studentProfile(profile).course(otherCourse).appliedDivision(majorRequired)
                .rawCourseCode("SM208").rawCourseName("기능해부학").credit(3)
                .takenYear(2025).takenSemester(Semester.SECOND)
                .status(CourseStatus.COMPLETED).source(RecordSource.PDF).isRetake(false).build());

        mockMvc.perform(get("/api/v1/students/me/graduation/MAJOR_REQUIRED/courses")
                        .param("studentMajorId", String.valueOf(main.getId()))
                        .with(authentication(authenticationOf(profile.getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.majors[0].courses.length()").value(2))
                .andExpect(jsonPath("$.data.majors[0].courses[?(@.name=='운동손상')].taken").value(true))
                .andExpect(jsonPath("$.data.majors[0].courses[?(@.name=='기능해부학')].taken").value(true));
    }

    // 검수 화면에서 과목 검색 없이 직접 입력한 과목(RecordSource.MANUAL)은 과목 마스터와 매칭되지
    // 않아 course=null이다. dedupeByCourse가 course=null 행을 통째로 걸러내던 탓에 이수구분별 상세
    // 목록에서 완전히 빠지는 버그가 있었다(#218) - 총 이수학점 계산에는 반영되는데 이 목록에는 안
    // 뜨는 모순이 있었다.
    @Test
    void 직접_추가한_미매칭_과목도_전공필수_이수과목_목록에_뜬다() throws Exception {
        School school = schoolRepository.save(School.builder().name("경희대학교-9502").build());
        Department department = departmentRepository.save(Department.builder()
                .school(school).college("체육대학").name("스포츠의학과-9502").build());
        Member member = memberRepository.save(Member.builder()
                .nickname("테스트유저").oauthProvider(OauthProvider.KAKAO).oauthId("9502").email(null).build());
        StudentProfile profile = studentProfileRepository.save(StudentProfile.builder()
                .member(member).school(school).department(department).admissionYear(2023).build());
        StudentMajor main = studentMajorRepository.save(StudentMajor.builder()
                .studentProfile(profile).department(department).majorType(MajorType.MAIN).build());
        graduationAnalysisSummaryRepository.save(GraduationAnalysisSummary.builder()
                .studentMajor(main)
                .majorRequiredCurrent(3).majorRequiredRequired(3)
                .build());

        Division majorRequired = divisionRepository.save(Division.builder()
                .school(school).code("04").category(DivisionCategory.MAJOR_REQUIRED).build());

        // 과목 검색 없이 직접 입력 - course/rawCourseCode 둘 다 null (편입학점 등 과목 마스터에
        // 아예 없는 과목을 수동으로 추가한 경우를 재현)
        studentCourseRepository.save(StudentCourse.builder()
                .studentProfile(profile).course(null).appliedDivision(majorRequired)
                .rawCourseCode(null).rawCourseName("편입인정과목").credit(3)
                .takenYear(2023).takenSemester(Semester.FIRST)
                .status(CourseStatus.COMPLETED).source(RecordSource.MANUAL).isRetake(false).build());

        mockMvc.perform(get("/api/v1/students/me/graduation/MAJOR_REQUIRED/courses")
                        .param("studentMajorId", String.valueOf(main.getId()))
                        .with(authentication(authenticationOf(profile.getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.majors[0].courses.length()").value(1))
                .andExpect(jsonPath("$.data.majors[0].courses[0].name").value("편입인정과목"))
                .andExpect(jsonPath("$.data.majors[0].courses[0].taken").value(true));
    }
}
