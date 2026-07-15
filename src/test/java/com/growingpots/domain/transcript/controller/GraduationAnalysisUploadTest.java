package com.growingpots.domain.transcript.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.growingpots.domain.transcript.entity.enums.CertJudgement;
import com.growingpots.domain.transcript.entity.CertResult;
import com.growingpots.domain.transcript.entity.enums.CertType;
import com.growingpots.domain.transcript.entity.GraduationAnalysisSummary;
import com.growingpots.domain.transcript.parser.ParsedTranscript;
import com.growingpots.domain.transcript.parser.PdfTranscriptParser;
import com.growingpots.domain.transcript.repository.CertResultRepository;
import com.growingpots.domain.transcript.repository.GraduationAnalysisSummaryRepository;
import com.growingpots.domain.university.entity.Department;
import com.growingpots.domain.university.entity.School;
import com.growingpots.domain.university.repository.DepartmentRepository;
import com.growingpots.domain.university.repository.SchoolRepository;
import com.growingpots.domain.user.entity.Member;
import com.growingpots.domain.user.entity.StudentMajor;
import com.growingpots.domain.user.entity.StudentMajor.MajorType;
import com.growingpots.domain.user.entity.StudentProfile;
import com.growingpots.domain.user.entity.enums.OauthProvider;
import com.growingpots.domain.user.repository.MemberRepository;
import com.growingpots.domain.user.repository.StudentMajorRepository;
import com.growingpots.domain.user.repository.StudentProfileRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class GraduationAnalysisUploadTest {

    private static final byte[] PDF_BYTES = "%PDF-1.4 fake content".getBytes(StandardCharsets.UTF_8);

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
    private CertResultRepository certResultRepository;

    @Autowired
    private SchoolRepository schoolRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @MockitoBean
    private PdfTranscriptParser pdfTranscriptParser;

    // 테스트가 같은 H2 컨텍스트를 공유해서 School.name unique 제약에 걸리지 않도록 이미 있으면 재사용한다.
    private Department department(String name) {
        return departmentRepository.findAll().stream()
                .filter(department -> name.equals(department.getName()))
                .findFirst()
                .orElseGet(() -> {
                    School school = schoolRepository.save(School.builder().name("경희대학교-" + name).build());
                    return departmentRepository.save(Department.builder()
                            .school(school)
                            .college("체육대학")
                            .name(name)
                            .build());
                });
    }

    // 온보딩(StudentProfile 생성)까지 마친 학생을 만든다. PDF 업로드는 이 학생이 존재해야만 가능하다.
    private StudentProfile onboardedStudent(String oauthId, Department department) {
        Member member = memberRepository.save(Member.builder()
                .nickname("테스트유저")
                .oauthProvider(OauthProvider.KAKAO)
                .oauthId(oauthId)
                .email(null)
                .build());
        return studentProfileRepository.save(StudentProfile.builder()
                .member(member)
                .school(department.getSchool())
                .department(department)
                .admissionYear(2023)
                .build());
    }

    @Test
    void 전공별_졸업요건_스냅샷과_비학점요건_5종이_저장된다() throws Exception {
        Department sportsScience = department("스포츠의학과");
        StudentProfile studentProfile = onboardedStudent("9001", sportsScience);
        when(pdfTranscriptParser.parse(any())).thenReturn(singleMajorTranscript());

        upload(studentProfile.getMember().getId());

        StudentMajor major = studentMajorRepository.findByStudentProfileAndDepartment(studentProfile, sportsScience).orElseThrow();
        assertThat(major.getMajorType()).isEqualTo(MajorType.MAIN);

        GraduationAnalysisSummary summary = graduationAnalysisSummaryRepository.findByStudentMajor(major).orElseThrow();
        assertThat(summary.getTotalCreditCurrent()).isEqualTo(62); // "44(62)": 44=완료, 62=완료+수강중 합계
        assertThat(summary.getTotalCreditRequired()).isEqualTo(120);
        assertThat(summary.getGpaCurrent()).isEqualByComparingTo(new BigDecimal("2.788"));
        assertThat(summary.getMajorBasicCurrent()).isEqualTo(6);
        assertThat(summary.getMajorBasicRequired()).isEqualTo(7);
        assertThat(summary.getRequiredGeCurrent()).isEqualTo(12);
        assertThat(summary.getDistributedGeCurrent()).isEqualTo(3);
        assertThat(summary.getFreeGeCurrent()).isEqualTo(5);

        List<CertResult> certResults = certResultRepository.findAll().stream()
                .filter(c -> studentProfile.getId().equals(c.getStudentProfile().getId()))
                .toList();
        assertThat(certResults).hasSize(5);
        assertThat(certResultOf(certResults, CertType.SW)).isEqualTo(CertJudgement.PASS);
        assertThat(certResultOf(certResults, CertType.GRADUATION_CERT)).isEqualTo(CertJudgement.FAIL);
        assertThat(certResultOf(certResults, CertType.TOPIK)).isEqualTo(CertJudgement.NONE);
        assertThat(certResultOf(certResults, CertType.ENGLISH)).isEqualTo(CertJudgement.PASS);
        assertThat(certResultOf(certResults, CertType.THESIS)).isEqualTo(CertJudgement.FAIL);
    }

    @Test
    void 재업로드하면_summary는_갱신되고_cert_result는_교체된다() throws Exception {
        Department sportsScience = department("스포츠의학과");
        StudentProfile studentProfile = onboardedStudent("9002", sportsScience);
        when(pdfTranscriptParser.parse(any())).thenReturn(singleMajorTranscript());
        upload(studentProfile.getMember().getId());

        Map<String, String> updatedMajorRequirement = Map.ofEntries(
                Map.entry("majorType", "단일전공"),
                Map.entry("majorSequence", "1"),
                Map.entry("majorName", "스포츠의학"),
                Map.entry("standardYear", "2024"),
                Map.entry("basicEarned", "7"), Map.entry("basicRequired", "7"),
                Map.entry("requiredEarned", "9"), Map.entry("requiredRequired", "9"),
                Map.entry("electiveEarned", "20"), Map.entry("electiveRequired", "51"),
                Map.entry("requiredPlusElectiveEarned", "29"), Map.entry("requiredPlusElectiveRequired", "60")
        );
        ParsedTranscript updated = new ParsedTranscript(
                Map.of(), graduationSummary(), generalEducation(), List.of(updatedMajorRequirement), List.of());
        when(pdfTranscriptParser.parse(any())).thenReturn(updated);

        upload(studentProfile.getMember().getId());

        assertThat(studentMajorRepository.findAll().stream()
                .filter(m -> studentProfile.getId().equals(m.getStudentProfile().getId()))).hasSize(1);
        StudentMajor major = studentMajorRepository.findByStudentProfileAndDepartment(studentProfile, sportsScience).orElseThrow();
        GraduationAnalysisSummary summary = graduationAnalysisSummaryRepository.findByStudentMajor(major).orElseThrow();
        assertThat(summary.getMajorElectiveCurrent()).isEqualTo(20);

        List<CertResult> certResults = certResultRepository.findAll().stream()
                .filter(c -> studentProfile.getId().equals(c.getStudentProfile().getId()))
                .toList();
        assertThat(certResults).hasSize(5);
    }

    // 학과가 다른 PDF를 재업로드하면, 예전 학과의 STUDENT_MAJOR/GRADUATION_ANALYSIS_SUMMARY가
    // 지워지고 새 학과 것만 남아야 한다(안 지우면 MAIN 타입 STUDENT_MAJOR가 계속 쌓이는 버그).
    @Test
    void 학과가_다른_PDF를_재업로드하면_예전_학과의_STUDENT_MAJOR가_삭제된다() throws Exception {
        Department sportsScience = department("스포츠의학과");
        Department digitalContents = department("디지털콘텐츠학과");
        StudentProfile studentProfile = onboardedStudent("9004", sportsScience);
        when(pdfTranscriptParser.parse(any())).thenReturn(singleMajorTranscript());
        upload(studentProfile.getMember().getId());

        Map<String, String> newMajorRequirement = Map.ofEntries(
                Map.entry("majorType", "단일전공"),
                Map.entry("majorSequence", "1"),
                Map.entry("majorName", "디지털콘텐츠학"),
                Map.entry("standardYear", "2024"),
                Map.entry("basicEarned", "8"), Map.entry("basicRequired", "8"),
                Map.entry("requiredEarned", "6"), Map.entry("requiredRequired", "6"),
                Map.entry("electiveEarned", "12"), Map.entry("electiveRequired", "45"),
                Map.entry("requiredPlusElectiveEarned", "18"), Map.entry("requiredPlusElectiveRequired", "51")
        );
        ParsedTranscript newDeptTranscript = new ParsedTranscript(
                Map.of(), graduationSummary(), generalEducation(), List.of(newMajorRequirement), List.of());
        when(pdfTranscriptParser.parse(any())).thenReturn(newDeptTranscript);

        upload(studentProfile.getMember().getId());

        List<StudentMajor> remainingMajors = studentMajorRepository.findAll().stream()
                .filter(m -> studentProfile.getId().equals(m.getStudentProfile().getId()))
                .toList();
        assertThat(remainingMajors).hasSize(1);
        assertThat(remainingMajors.getFirst().getDepartment().getId()).isEqualTo(digitalContents.getId());
        assertThat(studentMajorRepository.findByStudentProfileAndDepartment(studentProfile, sportsScience)).isEmpty();

        StudentMajor newMajor = remainingMajors.getFirst();
        assertThat(graduationAnalysisSummaryRepository.findByStudentMajor(newMajor)).isPresent();
        assertThat(graduationAnalysisSummaryRepository.findAll().stream()
                .filter(s -> s.getStudentMajor().getStudentProfile().getId().equals(studentProfile.getId())))
                .hasSize(1);
    }

    @Test
    void 복수전공이면_교양값이_전공별로_동일하게_복제된다() throws Exception {
        Department physicalEducation = department("체육학과");
        Department sportsScience = department("스포츠의학과");
        StudentProfile studentProfile = onboardedStudent("9003", physicalEducation);
        Map<String, String> mainMajor = Map.ofEntries(
                Map.entry("majorType", "단일전공"), Map.entry("majorSequence", "1"),
                Map.entry("majorName", "체육학"), Map.entry("standardYear", "2024"),
                Map.entry("basicEarned", "6"), Map.entry("basicRequired", "7"),
                Map.entry("requiredEarned", "9"), Map.entry("requiredRequired", "9"),
                Map.entry("electiveEarned", "15"), Map.entry("electiveRequired", "51"),
                Map.entry("requiredPlusElectiveEarned", "24"), Map.entry("requiredPlusElectiveRequired", "60")
        );
        Map<String, String> doubleMajor = Map.ofEntries(
                Map.entry("majorType", "복수전공"), Map.entry("majorSequence", "2"),
                Map.entry("majorName", "스포츠의학"), Map.entry("standardYear", "2024"),
                Map.entry("basicEarned", "3"), Map.entry("basicRequired", "6"),
                Map.entry("requiredEarned", "5"), Map.entry("requiredRequired", "9"),
                Map.entry("electiveEarned", "10"), Map.entry("electiveRequired", "40"),
                Map.entry("requiredPlusElectiveEarned", "15"), Map.entry("requiredPlusElectiveRequired", "49")
        );
        when(pdfTranscriptParser.parse(any())).thenReturn(new ParsedTranscript(
                Map.of(), graduationSummary(), generalEducation(), List.of(mainMajor, doubleMajor), List.of()));

        upload(studentProfile.getMember().getId());

        StudentMajor main = studentMajorRepository.findByStudentProfileAndDepartment(studentProfile, physicalEducation).orElseThrow();
        StudentMajor doubleM = studentMajorRepository.findByStudentProfileAndDepartment(studentProfile, sportsScience).orElseThrow();
        assertThat(main.getMajorType()).isEqualTo(MajorType.MAIN);
        assertThat(doubleM.getMajorType()).isEqualTo(MajorType.DOUBLE);

        GraduationAnalysisSummary mainSummary = graduationAnalysisSummaryRepository.findByStudentMajor(main).orElseThrow();
        GraduationAnalysisSummary doubleSummary = graduationAnalysisSummaryRepository.findByStudentMajor(doubleM).orElseThrow();
        assertThat(mainSummary.getRequiredGeCurrent()).isEqualTo(doubleSummary.getRequiredGeCurrent());
        assertThat(mainSummary.getDistributedGeCurrent()).isEqualTo(doubleSummary.getDistributedGeCurrent());
        assertThat(mainSummary.getFreeGeCurrent()).isEqualTo(doubleSummary.getFreeGeCurrent());
    }

    private void upload(Long memberId) throws Exception {
        mockMvc.perform(multipart("/api/v1/diagnosis/upload")
                        .file(new MockMultipartFile("file", "transcript.pdf", "application/pdf", PDF_BYTES))
                        .with(authentication(authenticationOf(memberId))))
                .andExpect(status().isCreated());
    }

    private CertJudgement certResultOf(List<CertResult> certResults, CertType certType) {
        return certResults.stream()
                .filter(c -> c.getCertType() == certType)
                .findFirst()
                .orElseThrow()
                .getResult();
    }

    private Authentication authenticationOf(Long memberId) {
        return new UsernamePasswordAuthenticationToken(memberId.toString(), null, Collections.emptyList());
    }

    private ParsedTranscript singleMajorTranscript() {
        Map<String, String> majorRequirement = Map.ofEntries(
                Map.entry("majorType", "심화전공"),
                Map.entry("majorSequence", "1"),
                Map.entry("majorName", "스포츠의학"),
                Map.entry("standardYear", "2024"),
                Map.entry("basicEarned", "6"), Map.entry("basicRequired", "7"),
                Map.entry("requiredEarned", "9"), Map.entry("requiredRequired", "9"),
                Map.entry("electiveEarned", "15"), Map.entry("electiveRequired", "51"),
                Map.entry("requiredPlusElectiveEarned", "24"), Map.entry("requiredPlusElectiveRequired", "60")
        );
        return new ParsedTranscript(Map.of(), graduationSummary(), generalEducation(), List.of(majorRequirement), List.of());
    }

    private Map<String, String> graduationSummary() {
        return Map.ofEntries(
                Map.entry("requiredCredits", "120"),
                Map.entry("earnedCredits", "44(62)"),
                Map.entry("gpaRequirement", "1.7"),
                Map.entry("gpaEarned", "픕2.788"),
                Map.entry("englishLectureRequirement", "3"),
                Map.entry("englishLectureEarned", "7"),
                Map.entry("topik", "해당없음"),
                Map.entry("englishLectureJudgement", "통과"),
                Map.entry("thesisJudgement", "미통과"),
                Map.entry("graduationCertification", "미통과"),
                Map.entry("swCertification", "통과")
        );
    }

    private List<Map<String, String>> generalEducation() {
        return List.of(
                Map.of("category", "배분이수교과(2024~)", "creditsEarned", "3", "creditsRequired", "9"),
                Map.of("category", "자유이수", "creditsEarned", "5", "creditsRequired", "3"),
                Map.of("category", "필수교과", "creditsEarned", "12", "creditsRequired", "17")
        );
    }
}
