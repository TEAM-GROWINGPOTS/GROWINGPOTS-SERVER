package com.growingpots.domain.user.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.growingpots.domain.university.entity.Department;
import com.growingpots.domain.university.entity.School;
import com.growingpots.domain.university.repository.DepartmentRepository;
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
class StudentProfileGetTest {

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

    private Authentication authenticationOf(Long memberId) {
        return new UsernamePasswordAuthenticationToken(memberId.toString(), null, Collections.emptyList());
    }

    @Test
    void PDF_분석으로_갱신된_학적정보가_그대로_조회된다() throws Exception {
        Member member = memberRepository.save(Member.builder()
                .nickname("김경민")
                .oauthProvider(OauthProvider.KAKAO)
                .oauthId("9001")
                .email(null)
                .build());
        School school = schoolRepository.save(School.builder().name("경희대학교-9001").build());
        Department department = departmentRepository.save(Department.builder()
                .school(school)
                .college("예술·디자인대학")
                .name("연극영화학과")
                .build());
        StudentProfile profile = studentProfileRepository.save(StudentProfile.builder()
                .member(member)
                .school(school)
                .department(department)
                .admissionYear(2019)
                .build());
        profile.updateAcademicInfo("2023123456", "재학", 3, 2023, 1);
        studentProfileRepository.save(profile);

        mockMvc.perform(get("/api/v1/students/me")
                        .with(authentication(authenticationOf(member.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.studentNo").value("2023123456"))
                .andExpect(jsonPath("$.data.gradeLevel").value(3))
                .andExpect(jsonPath("$.data.semester").value(1))
                .andExpect(jsonPath("$.data.enrollmentStatus").value("재학"))
                .andExpect(jsonPath("$.data.admissionYear").value(2023));
    }
}
