package com.growingpots.domain.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.growingpots.domain.transcript.entity.GraduationAnalysisSummary;
import com.growingpots.domain.transcript.repository.GraduationAnalysisSummaryRepository;
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

    // PDF를 이미 분석한 뒤(GraduationAnalysisSummary 있음)에는 기존처럼 막아야 한다(#221) - 이미 쌓인
    // 이수 과목 데이터가 붕 뜨는 걸 방지.
    @Test
    void PDF_분석_후에는_기본정보_재제출이_막힌다() throws Exception {
        Member member = member("8003");
        School school = schoolRepository.save(School.builder().name("경희대학교-8003").build());
        Department department = departmentRepository.save(Department.builder()
                .school(school).college("공과대학").name("컴퓨터공학과").build());

        StudentProfile profile = studentProfileRepository.save(StudentProfile.builder()
                .member(member).school(school).department(department).admissionYear(2022).build());
        StudentMajor mainMajor = studentMajorRepository.save(StudentMajor.builder()
                .studentProfile(profile).department(department)
                .majorType(StudentMajor.MajorType.MAIN).track(null).build());
        graduationAnalysisSummaryRepository.save(GraduationAnalysisSummary.builder()
                .studentMajor(mainMajor)
                .build());

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
