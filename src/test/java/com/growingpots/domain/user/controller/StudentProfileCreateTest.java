package com.growingpots.domain.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.growingpots.domain.transcript.entity.CertResult;
import com.growingpots.domain.transcript.entity.GraduationAnalysisSummary;
import com.growingpots.domain.transcript.entity.StudentCourse;
import com.growingpots.domain.transcript.entity.enums.CertJudgement;
import com.growingpots.domain.transcript.entity.enums.CertType;
import com.growingpots.domain.transcript.entity.enums.CourseStatus;
import com.growingpots.domain.transcript.entity.enums.RecordSource;
import com.growingpots.domain.transcript.entity.enums.Semester;
import com.growingpots.domain.transcript.repository.CertResultRepository;
import com.growingpots.domain.transcript.repository.GraduationAnalysisSummaryRepository;
import com.growingpots.domain.transcript.repository.StudentCourseRepository;
import com.growingpots.domain.university.entity.Department;
import com.growingpots.domain.university.entity.School;
import com.growingpots.domain.university.repository.DepartmentRepository;
import com.growingpots.domain.university.repository.SchoolRepository;
import com.growingpots.domain.user.entity.Member;
import com.growingpots.domain.user.entity.StudentMajor;
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

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class StudentProfileCreateTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private StudentProfileRepository studentProfileRepository;

    @Autowired
    private StudentMajorRepository studentMajorRepository;

    @Autowired
    private SchoolRepository schoolRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private GraduationAnalysisSummaryRepository graduationAnalysisSummaryRepository;

    @Autowired
    private StudentCourseRepository studentCourseRepository;

    @Autowired
    private CertResultRepository certResultRepository;

    private Authentication authenticationOf(Long memberId) {
        return new UsernamePasswordAuthenticationToken(memberId.toString(), null, Collections.emptyList());
    }

    private Member member(String oauthId) {
        return memberRepository.save(Member.builder()
                .nickname("테스트유저")
                .oauthProvider(OauthProvider.KAKAO)
                .oauthId(oauthId)
                .email(null)
                .build());
    }

    @Test
    void 처음_생성하면_201로_프로필이_만들어진다() throws Exception {
        Member member = member("8001");
        School school = schoolRepository.save(School.builder().name("경희대학교-8001").build());
        Department department = departmentRepository.save(Department.builder()
                .school(school).college("공과대학").name("컴퓨터공학과").build());

        mockMvc.perform(post("/api/v1/students")
                        .with(authentication(authenticationOf(member.getId())))
                        .contentType("application/json")
                        .content("""
                                {"schoolId": %d, "departmentId": %d, "admissionYear": 2023}
                                """.formatted(school.getId(), department.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.mainMajor.departmentName").value("컴퓨터공학과"));

        StudentProfile saved = studentProfileRepository.findByMember(member).orElseThrow();
        assertThat(saved.getAdmissionYear()).isEqualTo(2023);
    }

    // 검수 화면 전(PDF 미분석)에 기본정보입력 화면으로 돌아가서 다시 제출하면, 새 값으로 덮어써야 한다(#221).
    @Test
    void PDF_분석_전이면_기본정보를_다시_제출해도_수정된다() throws Exception {
        Member member = member("8002");
        School school = schoolRepository.save(School.builder().name("경희대학교-8002").build());
        Department oldDepartment = departmentRepository.save(Department.builder()
                .school(school).college("공과대학").name("컴퓨터공학과").build());
        Department newDepartment = departmentRepository.save(Department.builder()
                .school(school).college("예술·디자인대학").name("연극영화학과").build());

        StudentProfile profile = studentProfileRepository.save(StudentProfile.builder()
                .member(member).school(school).department(oldDepartment).admissionYear(2022).build());
        StudentMajor mainMajor = studentMajorRepository.save(StudentMajor.builder()
                .studentProfile(profile).department(oldDepartment)
                .majorType(StudentMajor.MajorType.MAIN).track(null).build());

        mockMvc.perform(post("/api/v1/students")
                        .with(authentication(authenticationOf(member.getId())))
                        .contentType("application/json")
                        .content("""
                                {"schoolId": %d, "departmentId": %d, "admissionYear": 2023}
                                """.formatted(school.getId(), newDepartment.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.studentProfileId").value(profile.getId()))
                .andExpect(jsonPath("$.data.mainMajor.studentMajorId").value(mainMajor.getId()))
                .andExpect(jsonPath("$.data.mainMajor.departmentName").value("연극영화학과"));

        StudentProfile updated = studentProfileRepository.findById(profile.getId()).orElseThrow();
        assertThat(updated.getDepartment().getId()).isEqualTo(newDepartment.getId());
        assertThat(updated.getAdmissionYear()).isEqualTo(2023);
        StudentMajor updatedMajor = studentMajorRepository.findById(mainMajor.getId()).orElseThrow();
        assertThat(updatedMajor.getDepartment().getId()).isEqualTo(newDepartment.getId());

        // 새로 만들어진 게 아니라 기존 row가 수정된 것이어야 한다(중복 row 생성 금지).
        assertThat(studentProfileRepository.findByMember(member).orElseThrow().getId()).isEqualTo(profile.getId());
    }

    // PDF 분석까지 끝났어도 아직 분석확인 화면에서 "확인"을 안 눌렀으면(onboardingConfirmedAt 없음)
    // 기본정보 재제출을 허용한다. 이때 이미 분석된 데이터는 새 학과 기준과 어긋날 수 있어 함께 지워서
    // PDF를 다시 올려야 재분석되도록 한다.
    @Test
    void 확인_전이면_PDF_분석_후에도_재제출이_허용되고_기존_분석데이터가_삭제된다() throws Exception {
        Member member = member("8003");
        School school = schoolRepository.save(School.builder().name("경희대학교-8003").build());
        Department oldDepartment = departmentRepository.save(Department.builder()
                .school(school).college("공과대학").name("컴퓨터공학과").build());
        Department newDepartment = departmentRepository.save(Department.builder()
                .school(school).college("예술·디자인대학").name("연극영화학과").build());

        StudentProfile profile = studentProfileRepository.save(StudentProfile.builder()
                .member(member).school(school).department(oldDepartment).admissionYear(2022).build());
        StudentMajor mainMajor = studentMajorRepository.save(StudentMajor.builder()
                .studentProfile(profile).department(oldDepartment)
                .majorType(StudentMajor.MajorType.MAIN).track(null).build());
        graduationAnalysisSummaryRepository.save(GraduationAnalysisSummary.builder()
                .studentMajor(mainMajor)
                .build());
        certResultRepository.save(CertResult.builder()
                .studentProfile(profile).studentMajor(mainMajor)
                .certType(CertType.ENGLISH).result(CertJudgement.PASS).source(RecordSource.PDF).build());
        studentCourseRepository.save(StudentCourse.builder()
                .studentProfile(profile).course(null).appliedDivision(null)
                .rawCourseCode(null).rawCourseName("옛학과기준과목").credit(3)
                .takenYear(2022).takenSemester(Semester.FIRST)
                .status(CourseStatus.COMPLETED).source(RecordSource.PDF).isRetake(false).build());

        mockMvc.perform(post("/api/v1/students")
                        .with(authentication(authenticationOf(member.getId())))
                        .contentType("application/json")
                        .content("""
                                {"schoolId": %d, "departmentId": %d, "admissionYear": 2023}
                                """.formatted(school.getId(), newDepartment.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.mainMajor.departmentName").value("연극영화학과"));

        assertThat(graduationAnalysisSummaryRepository.findByStudentMajor(mainMajor)).isEmpty();
        assertThat(certResultRepository.findByStudentMajor(mainMajor)).isEmpty();
        assertThat(studentCourseRepository.findByStudentProfile(profile)).isEmpty();
    }

    // 분석확인 화면에서 "확인"까지 누른 뒤(onboardingConfirmedAt 있음)에는 기존처럼 막아야 한다 - 이미
    // 확정된 학적 정보와 분석 데이터가 재제출로 어긋나는 걸 방지.
    @Test
    void 확인_후에는_기본정보_재제출이_막힌다() throws Exception {
        Member member = member("8004");
        School school = schoolRepository.save(School.builder().name("경희대학교-8004").build());
        Department department = departmentRepository.save(Department.builder()
                .school(school).college("공과대학").name("컴퓨터공학과").build());

        StudentProfile profile = studentProfileRepository.save(StudentProfile.builder()
                .member(member).school(school).department(department).admissionYear(2022).build());
        studentMajorRepository.save(StudentMajor.builder()
                .studentProfile(profile).department(department)
                .majorType(StudentMajor.MajorType.MAIN).track(null).build());
        profile.confirmOnboarding();
        studentProfileRepository.save(profile);

        mockMvc.perform(post("/api/v1/students")
                        .with(authentication(authenticationOf(member.getId())))
                        .contentType("application/json")
                        .content("""
                                {"schoolId": %d, "departmentId": %d, "admissionYear": 2023}
                                """.formatted(school.getId(), department.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_002"));
    }
}
