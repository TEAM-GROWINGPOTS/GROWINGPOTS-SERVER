package com.growingpots.domain.planner.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.growingpots.domain.planner.entity.PlannerSimulation;
import com.growingpots.domain.planner.entity.PlannerTerm;
import com.growingpots.domain.planner.entity.PlannerTermVersion;
import com.growingpots.domain.planner.repository.PlannerSimulationRepository;
import com.growingpots.domain.planner.repository.PlannerTermRepository;
import com.growingpots.domain.planner.repository.PlannerTermVersionRepository;
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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class SelectVersionTest {

    @Autowired MockMvc mockMvc;
    @Autowired MemberRepository memberRepository;
    @Autowired StudentProfileRepository studentProfileRepository;
    @Autowired SchoolRepository schoolRepository;
    @Autowired DepartmentRepository departmentRepository;
    @Autowired PlannerSimulationRepository plannerSimulationRepository;
    @Autowired PlannerTermRepository plannerTermRepository;
    @Autowired PlannerTermVersionRepository plannerTermVersionRepository;

    private StudentProfile createStudent(String oauthId) {
        School school = schoolRepository.save(School.builder().name("테스트대학교-" + oauthId).build());
        Department dept = departmentRepository.save(
                Department.builder().school(school).college("공과대학").name("컴퓨터공학과").build());
        Member member = memberRepository.save(Member.builder()
                .nickname("유저-" + oauthId).oauthProvider(OauthProvider.KAKAO)
                .oauthId(oauthId).email(null).build());
        return studentProfileRepository.save(StudentProfile.builder()
                .member(member).school(school).department(dept).admissionYear(2023).build());
    }

    private PlannerTerm createTerm(PlannerSimulation simulation, int yearLevel, int semester) {
        return plannerTermRepository.save(PlannerTerm.builder()
                .plannerSimulation(simulation).yearLevel(yearLevel).semester(semester).build());
    }

    private PlannerTermVersion createVersion(PlannerTerm term, int versionNo, boolean isSelected) {
        return plannerTermVersionRepository.save(PlannerTermVersion.builder()
                .plannerTerm(term).versionNo(versionNo).name(null).isSelected(isSelected).versionOrder(0).build());
    }

    private Authentication authOf(Long memberId) {
        return new UsernamePasswordAuthenticationToken(memberId.toString(), null, Collections.emptyList());
    }

    private String body(Long versionId) {
        return "{\"plannerTermVersionId\":" + versionId + "}";
    }

    @Test
    void 정상_전환_성공_응답에_termId와_selectedVersionId가_반환되고_DB_isSelected가_전환된다() throws Exception {
        StudentProfile student = createStudent("SV001");
        PlannerSimulation sim = plannerSimulationRepository.save(
                PlannerSimulation.builder().studentProfile(student).name("내 플래너").build());
        PlannerTerm term = createTerm(sim, 1, 1);
        PlannerTermVersion v1 = createVersion(term, 1, true);
        PlannerTermVersion v2 = createVersion(term, 2, false);

        mockMvc.perform(patch("/api/v1/planner/terms/{termId}/selected-version", term.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(v2.getId()))
                        .with(authentication(authOf(student.getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PLAN_200_4"))
                .andExpect(jsonPath("$.data.plannerTermId").value(term.getId()))
                .andExpect(jsonPath("$.data.selectedVersionId").value(v2.getId()));

        assertThat(plannerTermVersionRepository.findById(v1.getId()).orElseThrow().isSelected()).isFalse();
        assertThat(plannerTermVersionRepository.findById(v2.getId()).orElseThrow().isSelected()).isTrue();
    }

    @Test
    void 이미_선택된_버전을_다시_지정해도_200_멱등_DB_상태_유지() throws Exception {
        StudentProfile student = createStudent("SV002");
        PlannerSimulation sim = plannerSimulationRepository.save(
                PlannerSimulation.builder().studentProfile(student).name("내 플래너").build());
        PlannerTerm term = createTerm(sim, 1, 1);
        PlannerTermVersion v1 = createVersion(term, 1, true);
        PlannerTermVersion v2 = createVersion(term, 2, false);

        mockMvc.perform(patch("/api/v1/planner/terms/{termId}/selected-version", term.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(v1.getId()))
                        .with(authentication(authOf(student.getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PLAN_200_4"))
                .andExpect(jsonPath("$.data.selectedVersionId").value(v1.getId()));

        assertThat(plannerTermVersionRepository.findById(v1.getId()).orElseThrow().isSelected()).isTrue();
        assertThat(plannerTermVersionRepository.findById(v2.getId()).orElseThrow().isSelected()).isFalse();
    }

    @Test
    void 타인_소유_학기_접근시_404_PLAN_005() throws Exception {
        StudentProfile owner = createStudent("SV003-owner");
        StudentProfile other = createStudent("SV003-other");

        PlannerSimulation sim = plannerSimulationRepository.save(
                PlannerSimulation.builder().studentProfile(owner).name("내 플래너").build());
        PlannerTerm term = createTerm(sim, 1, 1);
        PlannerTermVersion v1 = createVersion(term, 1, true);

        mockMvc.perform(patch("/api/v1/planner/terms/{termId}/selected-version", term.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(v1.getId()))
                        .with(authentication(authOf(other.getMember().getId()))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLAN_005"));
    }

    @Test
    void 다른_학기_소속_versionId_지정시_404_PLAN_005() throws Exception {
        StudentProfile student = createStudent("SV004");
        PlannerSimulation sim = plannerSimulationRepository.save(
                PlannerSimulation.builder().studentProfile(student).name("내 플래너").build());

        PlannerTerm term1 = createTerm(sim, 1, 1);
        PlannerTermVersion v1 = createVersion(term1, 1, true);

        PlannerTerm term2 = createTerm(sim, 1, 2);
        PlannerTermVersion v2 = createVersion(term2, 1, true);

        // term1 엔드포인트에 term2의 versionId 지정
        mockMvc.perform(patch("/api/v1/planner/terms/{termId}/selected-version", term1.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(v2.getId()))
                        .with(authentication(authOf(student.getMember().getId()))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLAN_005"));
    }

    @Test
    void plannerTermVersionId_null_요청시_400_CMN_002() throws Exception {
        StudentProfile student = createStudent("SV005");
        PlannerSimulation sim = plannerSimulationRepository.save(
                PlannerSimulation.builder().studentProfile(student).name("내 플래너").build());
        PlannerTerm term = createTerm(sim, 1, 1);

        mockMvc.perform(patch("/api/v1/planner/terms/{termId}/selected-version", term.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plannerTermVersionId\":null}")
                        .with(authentication(authOf(student.getMember().getId()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CMN_002"));
    }

    @Test
    void 미인증_요청시_401_CMN_005() throws Exception {
        mockMvc.perform(patch("/api/v1/planner/terms/1/selected-version")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(1L)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("CMN_005"));
    }

    @Test
    void 존재하지_않는_termId_요청시_404_PLAN_005() throws Exception {
        StudentProfile student = createStudent("SV006");

        mockMvc.perform(patch("/api/v1/planner/terms/999999/selected-version")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(1L))
                        .with(authentication(authOf(student.getMember().getId()))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLAN_005"));
    }

    // PLAN_006(locked 학기) 케이스는 테스트 안 함 — isTermLocked()가 항상 false라 트리거할 방법이
    // 없다(PlannerService.isTermLocked 주석 참고: 정상 흐름에서 PLANNER_TERM은 항상 미래 학기뿐).
}