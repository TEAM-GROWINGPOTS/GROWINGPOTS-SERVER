package com.growingpots.domain.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
class StudentOnboardingConfirmTest {

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
    void PDF_분석까지_끝났으면_확인_요청이_200으로_성공하고_확인_시각이_저장된다() throws Exception {
        Member member = member("9501");
        School school = schoolRepository.save(School.builder().name("경희대학교-9501").build());
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

        mockMvc.perform(patch("/api/v1/students/me/onboarding-confirm")
                        .with(authentication(authenticationOf(member.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("USER_200_5"));

        StudentProfile updated = studentProfileRepository.findById(profile.getId()).orElseThrow();
        assertThat(updated.getOnboardingConfirmedAt()).isNotNull();
    }

    // 분석확인 화면 자체에 아직 도달할 수 없는 상태(PDF 미분석)에서 호출되면 막아야 한다.
    @Test
    void PDF를_아직_분석하지_않았으면_400_USER_005를_반환한다() throws Exception {
        Member member = member("9502");
        School school = schoolRepository.save(School.builder().name("경희대학교-9502").build());
        Department department = departmentRepository.save(Department.builder()
                .school(school).college("공과대학").name("컴퓨터공학과").build());
        StudentProfile profile = studentProfileRepository.save(StudentProfile.builder()
                .member(member).school(school).department(department).admissionYear(2022).build());
        studentMajorRepository.save(StudentMajor.builder()
                .studentProfile(profile).department(department)
                .majorType(StudentMajor.MajorType.MAIN).track(null).build());

        mockMvc.perform(patch("/api/v1/students/me/onboarding-confirm")
                        .with(authentication(authenticationOf(member.getId()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("USER_005"));
    }

    @Test
    void 학생_프로필이_없으면_404_USER_003을_반환한다() throws Exception {
        Member member = member("9503");

        mockMvc.perform(patch("/api/v1/students/me/onboarding-confirm")
                        .with(authentication(authenticationOf(member.getId()))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_003"));
    }

    // 중복 클릭 등으로 이미 확인한 상태에서 다시 호출돼도 에러 없이 성공해야 한다(멱등).
    @Test
    void 이미_확인한_상태에서_다시_호출해도_에러없이_성공한다() throws Exception {
        Member member = member("9504");
        School school = schoolRepository.save(School.builder().name("경희대학교-9504").build());
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
        profile.confirmOnboarding();
        studentProfileRepository.save(profile);

        mockMvc.perform(patch("/api/v1/students/me/onboarding-confirm")
                        .with(authentication(authenticationOf(member.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("USER_200_5"));
    }
}
