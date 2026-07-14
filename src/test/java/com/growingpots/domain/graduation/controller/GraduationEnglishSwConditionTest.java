package com.growingpots.domain.graduation.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.growingpots.domain.planner.entity.PlannerSimulation;
import com.growingpots.domain.planner.entity.PlannerTerm;
import com.growingpots.domain.planner.entity.PlannerTermVersion;
import com.growingpots.domain.planner.entity.PlannerVersionItem;
import com.growingpots.domain.planner.repository.PlannerSimulationRepository;
import com.growingpots.domain.planner.repository.PlannerTermRepository;
import com.growingpots.domain.planner.repository.PlannerTermVersionRepository;
import com.growingpots.domain.planner.repository.PlannerVersionItemRepository;
import com.growingpots.domain.transcript.entity.GraduationAnalysisSummary;
import com.growingpots.domain.transcript.repository.GraduationAnalysisSummaryRepository;
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

// 영어강의/SW인증 이수 현황이 본전공(=전체) 탭에서만 실제 값을 보여주고, 복수전공·교양 탭은 항상 0으로
// 고정되는지 검증한다(#관련: 실제 SW인증 과목이 교양(자유이수)에 몰려있어 전공 탭 라이브 재계산 시 0으로
// 나오던 버그 - GraduationAnalysisSummary 스냅샷 값을 그대로 쓰도록 수정).
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class GraduationEnglishSwConditionTest {

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
    private PlannerSimulationRepository plannerSimulationRepository;

    @Autowired
    private PlannerTermRepository plannerTermRepository;

    @Autowired
    private PlannerTermVersionRepository plannerTermVersionRepository;

    @Autowired
    private PlannerVersionItemRepository plannerVersionItemRepository;

    private Authentication authenticationOf(Long memberId) {
        return new UsernamePasswordAuthenticationToken(memberId.toString(), null, Collections.emptyList());
    }

    @Test
    void 본전공_탭에서는_SW_영어_현황이_PDF_스냅샷_값으로_노출된다() throws Exception {
        School school = schoolRepository.save(School.builder().name("경희대학교-9301").build());
        Department department = departmentRepository.save(Department.builder()
                .school(school).college("공과대학").name("컴퓨터공학과-9301").build());
        Member member = memberRepository.save(Member.builder()
                .nickname("테스트유저").oauthProvider(OauthProvider.KAKAO).oauthId("9301").email(null).build());
        StudentProfile profile = studentProfileRepository.save(StudentProfile.builder()
                .member(member).school(school).department(department).admissionYear(2023).build());
        StudentMajor main = studentMajorRepository.save(StudentMajor.builder()
                .studentProfile(profile).department(department).majorType(MajorType.MAIN).build());
        graduationAnalysisSummaryRepository.save(GraduationAnalysisSummary.builder()
                .studentMajor(main)
                .englishCurrent(5).englishRequired(3)
                .swCertCurrent(4).swCertRequired(6)
                .build());

        mockMvc.perform(get("/api/v1/students/me/graduation")
                        .param("studentMajorId", String.valueOf(main.getId()))
                        .with(authentication(authenticationOf(profile.getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.conditions[?(@.code=='ENGLISH_COURSE')].current").value(5))
                .andExpect(jsonPath("$.data.conditions[?(@.code=='ENGLISH_COURSE')].required").value(3))
                .andExpect(jsonPath("$.data.conditions[?(@.code=='ENGLISH_COURSE')].satisfied").value(true))
                .andExpect(jsonPath("$.data.conditions[?(@.code=='SW_CERT_COURSE')].current").value(4))
                .andExpect(jsonPath("$.data.conditions[?(@.code=='SW_CERT_COURSE')].required").value(6))
                .andExpect(jsonPath("$.data.conditions[?(@.code=='SW_CERT_COURSE')].satisfied").value(false));
    }

    // PDF의 영어/SW 요약은 학생 전체 기준 값 하나뿐이라 본전공·복수전공 GraduationAnalysisSummary 행에
    // 똑같이 저장돼 있더라도(TranscriptPersister 저장 방식), 복수전공 탭·교양 탭에서는 0으로 고정해야
    // 한다 - 영어/SW는 '전체'(본전공) 탭에서만 노출하기로 기획 확정.
    @Test
    void 복수전공_탭과_교양_탭에서는_SW_영어가_0으로_고정된다() throws Exception {
        School school = schoolRepository.save(School.builder().name("경희대학교-9302").build());
        Department mainDept = departmentRepository.save(Department.builder()
                .school(school).college("공과대학").name("컴퓨터공학과-9302").build());
        Department doubleDept = departmentRepository.save(Department.builder()
                .school(school).college("경영대학").name("경영학과-9302").build());
        Member member = memberRepository.save(Member.builder()
                .nickname("테스트유저").oauthProvider(OauthProvider.KAKAO).oauthId("9302").email(null).build());
        StudentProfile profile = studentProfileRepository.save(StudentProfile.builder()
                .member(member).school(school).department(mainDept).admissionYear(2023).build());
        StudentMajor main = studentMajorRepository.save(StudentMajor.builder()
                .studentProfile(profile).department(mainDept).majorType(MajorType.MAIN).build());
        StudentMajor doubleMajor = studentMajorRepository.save(StudentMajor.builder()
                .studentProfile(profile).department(doubleDept).majorType(MajorType.DOUBLE).build());
        // PDF 파싱 시점에 본전공/복수전공 행 모두 같은 전체 요약값이 그대로 복붙 저장되는 실제 상황을 재현.
        graduationAnalysisSummaryRepository.save(GraduationAnalysisSummary.builder()
                .studentMajor(main)
                .englishCurrent(5).englishRequired(3)
                .swCertCurrent(4).swCertRequired(6)
                .build());
        graduationAnalysisSummaryRepository.save(GraduationAnalysisSummary.builder()
                .studentMajor(doubleMajor)
                .englishCurrent(5).englishRequired(3)
                .swCertCurrent(4).swCertRequired(6)
                .build());

        mockMvc.perform(get("/api/v1/students/me/graduation")
                        .param("majorType", "ALL")
                        .with(authentication(authenticationOf(profile.getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sections.majors[0].majorType").value("MAIN"))
                .andExpect(jsonPath("$.data.sections.majors[0].conditions[?(@.code=='ENGLISH_COURSE')].current").value(5))
                .andExpect(jsonPath("$.data.sections.majors[0].conditions[?(@.code=='SW_CERT_COURSE')].current").value(4))
                .andExpect(jsonPath("$.data.sections.majors[1].majorType").value("DOUBLE"))
                .andExpect(jsonPath("$.data.sections.majors[1].conditions[?(@.code=='ENGLISH_COURSE')].current").value(0))
                .andExpect(jsonPath("$.data.sections.majors[1].conditions[?(@.code=='ENGLISH_COURSE')].required").value(3))
                .andExpect(jsonPath("$.data.sections.majors[1].conditions[?(@.code=='SW_CERT_COURSE')].current").value(0))
                .andExpect(jsonPath("$.data.sections.majors[1].conditions[?(@.code=='SW_CERT_COURSE')].required").value(6))
                .andExpect(jsonPath("$.data.sections.ge.conditions[?(@.code=='SW_CERT_COURSE')].current").value(0))
                .andExpect(jsonPath("$.data.sections.ge.conditions[?(@.code=='SW_CERT_COURSE')].required").value(6));

        // 복수전공 하나만 studentMajorId로 콕 집어 조회해도 여전히 0으로 고정돼야 한다.
        mockMvc.perform(get("/api/v1/students/me/graduation")
                        .param("studentMajorId", String.valueOf(doubleMajor.getId()))
                        .with(authentication(authenticationOf(profile.getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.conditions[?(@.code=='ENGLISH_COURSE')].current").value(0))
                .andExpect(jsonPath("$.data.conditions[?(@.code=='SW_CERT_COURSE')].current").value(0));
    }

    // 본전공 SW/영어 조건은 완료 과목(스냅샷) 기준이 COMPLETED일 땐 그대로, PLANNED(플래너 반영)일 땐
    // 계획한 SW/영어 과목의 학점·개수가 더해져야 한다.
    @Test
    void PLANNED_모드에서는_본전공에_한해_계획한_SW_영어_과목이_current에_더해진다() throws Exception {
        School school = schoolRepository.save(School.builder().name("경희대학교-9303").build());
        Department department = departmentRepository.save(Department.builder()
                .school(school).college("공과대학").name("컴퓨터공학과-9303").build());
        Member member = memberRepository.save(Member.builder()
                .nickname("테스트유저").oauthProvider(OauthProvider.KAKAO).oauthId("9303").email(null).build());
        StudentProfile profile = studentProfileRepository.save(StudentProfile.builder()
                .member(member).school(school).department(department).admissionYear(2023).build());
        StudentMajor main = studentMajorRepository.save(StudentMajor.builder()
                .studentProfile(profile).department(department).majorType(MajorType.MAIN).build());
        graduationAnalysisSummaryRepository.save(GraduationAnalysisSummary.builder()
                .studentMajor(main)
                .englishCurrent(0).englishRequired(1)
                .swCertCurrent(0).swCertRequired(6)
                .build());

        Division majorElective = divisionRepository.save(Division.builder()
                .school(school).code("05").category(DivisionCategory.MAJOR_ELECTIVE).build());
        Course swCourse = courseRepository.save(Course.builder()
                .school(school).courseCode("SWCON999").name("계획된SW과목").credit(3)
                .offeringDepartment(department).isEnglish(false).isSw(true).isActive(true).build());

        PlannerSimulation simulation = plannerSimulationRepository.save(PlannerSimulation.builder()
                .studentProfile(profile).name("내 플래너").build());
        PlannerTerm term = plannerTermRepository.save(PlannerTerm.builder()
                .plannerSimulation(simulation).yearLevel(2).semester(1).build());
        PlannerTermVersion version = plannerTermVersionRepository.save(PlannerTermVersion.builder()
                .plannerTerm(term).versionNo(1).name("폴더 1").isSelected(true).versionOrder(0).build());
        plannerVersionItemRepository.save(PlannerVersionItem.builder()
                .plannerTermVersion(version).course(swCourse).plannedDivision(majorElective)
                .credit(3).coursePositionOrder(0).build());

        mockMvc.perform(get("/api/v1/students/me/graduation")
                        .param("studentMajorId", String.valueOf(main.getId())).param("source", "COMPLETED")
                        .with(authentication(authenticationOf(profile.getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.conditions[?(@.code=='SW_CERT_COURSE')].current").value(0));

        mockMvc.perform(get("/api/v1/students/me/graduation")
                        .param("studentMajorId", String.valueOf(main.getId())).param("source", "PLANNED")
                        .with(authentication(authenticationOf(profile.getMember().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.conditions[?(@.code=='SW_CERT_COURSE')].current").value(3))
                .andExpect(jsonPath("$.data.conditions[?(@.code=='SW_CERT_COURSE')].satisfied").value(false));
    }
}
