package com.growingpots.domain.transcript.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.growingpots.domain.transcript.entity.enums.RecordSource;
import com.growingpots.domain.transcript.entity.enums.CourseStatus;
import com.growingpots.domain.transcript.entity.enums.Semester;
import com.growingpots.domain.transcript.entity.StudentCourse;
import com.growingpots.domain.transcript.parser.ParsedTranscript;
import com.growingpots.domain.transcript.parser.PdfParsingException;
import com.growingpots.domain.transcript.parser.PdfTranscriptParser;
import com.growingpots.domain.transcript.repository.StudentCourseRepository;
import com.growingpots.domain.university.entity.Department;
import com.growingpots.domain.university.entity.School;
import com.growingpots.domain.university.repository.DepartmentRepository;
import com.growingpots.domain.university.repository.SchoolRepository;
import com.growingpots.domain.user.entity.Member;
import com.growingpots.domain.user.entity.StudentProfile;
import com.growingpots.domain.user.entity.enums.OauthProvider;
import com.growingpots.domain.user.repository.MemberRepository;
import com.growingpots.domain.user.repository.StudentProfileRepository;
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
class TranscriptControllerTest {

    private static final byte[] PDF_BYTES = "%PDF-1.4 fake content".getBytes(StandardCharsets.UTF_8);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StudentCourseRepository studentCourseRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private StudentProfileRepository studentProfileRepository;

    @Autowired
    private SchoolRepository schoolRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @MockitoBean
    private PdfTranscriptParser pdfTranscriptParser;

    // 온보딩(StudentProfile 생성)까지 마친 학생을 만든다. PDF 업로드는 이 학생이 존재해야만 가능하다.
    private StudentProfile onboardedStudent(String oauthId) {
        Member member = memberRepository.save(Member.builder()
                .nickname("테스트유저")
                .oauthProvider(OauthProvider.KAKAO)
                .oauthId(oauthId)
                .email(null)
                .build());
        School school = schoolRepository.save(School.builder().name("경희대학교-" + oauthId).build());
        Department department = departmentRepository.save(Department.builder()
                .school(school)
                .college("공과대학")
                .name("컴퓨터공학과")
                .build());
        return studentProfileRepository.save(StudentProfile.builder()
                .member(member)
                .school(school)
                .department(department)
                .admissionYear(2023)
                .build());
    }

    @Test
    void 신규_사용자는_파싱된_과목을_저장하고_201을_반환한다() throws Exception {
        StudentProfile studentProfile = onboardedStudent("1001");
        when(pdfTranscriptParser.parse(any())).thenReturn(sampleParsedTranscript());

        mockMvc.perform(multipart("/api/v1/diagnosis/upload")
                        .file(new MockMultipartFile("file", "transcript.pdf", "application/pdf", PDF_BYTES))
                        .with(authentication(authenticationOf(studentProfile.getMember().getId()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("TRANS_201"));

        List<StudentCourse> saved = coursesOf(studentProfile);
        assertThat(saved).hasSize(1);
        StudentCourse course = saved.getFirst();
        assertThat(course.getRawCourseCode()).isEqualTo("GEC1104");
        assertThat(course.getTakenYear()).isEqualTo(2023);
        assertThat(course.getTakenSemester()).isEqualTo(Semester.FIRST);
        assertThat(course.getStatus()).isEqualTo(CourseStatus.COMPLETED);
        assertThat(course.getSource()).isEqualTo(RecordSource.PDF);
        assertThat(course.isRetake()).isFalse();
    }

    @Test
    void 재업로드하면_기존_PDF_데이터는_삭제되고_수동입력_데이터는_보존된다() throws Exception {
        StudentProfile studentProfile = onboardedStudent("2002");
        studentCourseRepository.save(StudentCourse.builder()
                .studentProfile(studentProfile)
                .rawCourseCode("OLD001")
                .rawCourseName("옛날 PDF 과목")
                .credit(3)
                .takenYear(2020)
                .takenSemester(Semester.FIRST)
                .section("기타")
                .status(CourseStatus.COMPLETED)
                .source(RecordSource.PDF)
                .build());
        studentCourseRepository.save(StudentCourse.builder()
                .studentProfile(studentProfile)
                .rawCourseCode("MAN001")
                .rawCourseName("수동으로 추가한 과목")
                .credit(2)
                .takenYear(2021)
                .takenSemester(Semester.SECOND)
                .section("자유이수")
                .status(CourseStatus.COMPLETED)
                .source(RecordSource.MANUAL)
                .build());

        when(pdfTranscriptParser.parse(any())).thenReturn(sampleParsedTranscript());

        mockMvc.perform(multipart("/api/v1/diagnosis/upload")
                        .file(new MockMultipartFile("file", "transcript.pdf", "application/pdf", PDF_BYTES))
                        .with(authentication(authenticationOf(studentProfile.getMember().getId()))))
                .andExpect(status().isCreated());

        List<StudentCourse> saved = coursesOf(studentProfile);
        assertThat(saved).extracting(StudentCourse::getRawCourseCode)
                .containsExactlyInAnyOrder("MAN001", "GEC1104");
    }

    @Test
    void PDF가_아니면_400_TRANS_002를_반환한다() throws Exception {
        StudentProfile studentProfile = onboardedStudent("3003");

        mockMvc.perform(multipart("/api/v1/diagnosis/upload")
                        .file(new MockMultipartFile("file", "not.pdf", "application/pdf",
                                "이건 PDF가 아님".getBytes(StandardCharsets.UTF_8)))
                        .with(authentication(authenticationOf(studentProfile.getMember().getId()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TRANS_002"));
    }

    @Test
    void 파싱에_실패하면_500_TRANS_001을_반환한다() throws Exception {
        StudentProfile studentProfile = onboardedStudent("4004");
        when(pdfTranscriptParser.parse(any())).thenThrow(new PdfParsingException("파싱 실패"));

        mockMvc.perform(multipart("/api/v1/diagnosis/upload")
                        .file(new MockMultipartFile("file", "transcript.pdf", "application/pdf", PDF_BYTES))
                        .with(authentication(authenticationOf(studentProfile.getMember().getId()))))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("TRANS_001"));
    }

    @Test
    void 인증_헤더가_없으면_401_CMN_005를_반환한다() throws Exception {
        mockMvc.perform(multipart("/api/v1/diagnosis/upload")
                        .file(new MockMultipartFile("file", "transcript.pdf", "application/pdf", PDF_BYTES)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("CMN_005"));
    }

    @Test
    void 온보딩_전이면_404_USER_003을_반환한다() throws Exception {
        Member member = memberRepository.save(Member.builder()
                .nickname("온보딩안한유저")
                .oauthProvider(OauthProvider.KAKAO)
                .oauthId("6006")
                .email(null)
                .build());

        mockMvc.perform(multipart("/api/v1/diagnosis/upload")
                        .file(new MockMultipartFile("file", "transcript.pdf", "application/pdf", PDF_BYTES))
                        .with(authentication(authenticationOf(member.getId()))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_003"));
    }

    @Test
    void 금학기수강학점_과목은_오늘_날짜_기준_학사년도_학기로_IN_PROGRESS로_저장된다() throws Exception {
        StudentProfile studentProfile = onboardedStudent("5005");
        Map<String, String> inProgressCourse = Map.of(
                "section", "금학기수강학점",
                "courseCode", "CSE3001",
                "courseName", "운영체제",
                "credits", "3"
        );
        when(pdfTranscriptParser.parse(any()))
                .thenReturn(new ParsedTranscript(Map.of(), Map.of(), List.of(), List.of(), List.of(inProgressCourse)));

        mockMvc.perform(multipart("/api/v1/diagnosis/upload")
                        .file(new MockMultipartFile("file", "transcript.pdf", "application/pdf", PDF_BYTES))
                        .with(authentication(authenticationOf(studentProfile.getMember().getId()))))
                .andExpect(status().isCreated());

        StudentCourse course = coursesOf(studentProfile).getFirst();
        assertThat(course.getStatus()).isEqualTo(CourseStatus.IN_PROGRESS);

        java.time.LocalDate now = java.time.LocalDate.now();
        int expectedYear = now.getMonthValue() <= 2 ? now.getYear() - 1 : now.getYear();
        Semester expectedTerm = (now.getMonthValue() >= 3 && now.getMonthValue() <= 8) ? Semester.FIRST : Semester.SECOND;
        assertThat(course.getTakenYear()).isEqualTo(expectedYear);
        assertThat(course.getTakenSemester()).isEqualTo(expectedTerm);
    }

    @Test
    void 파싱된_학생정보로_StudentProfile의_학적정보가_갱신된다() throws Exception {
        StudentProfile studentProfile = onboardedStudent("7007");
        Map<String, String> studentInfo = Map.of(
                "studentId", "2019123456",
                "grade", "4",
                "academicStatus", "재학"
        );
        when(pdfTranscriptParser.parse(any()))
                .thenReturn(new ParsedTranscript(studentInfo, Map.of(), List.of(), List.of(), sampleParsedTranscript().courses()));

        mockMvc.perform(multipart("/api/v1/diagnosis/upload")
                        .file(new MockMultipartFile("file", "transcript.pdf", "application/pdf", PDF_BYTES))
                        .with(authentication(authenticationOf(studentProfile.getMember().getId()))))
                .andExpect(status().isCreated());

        StudentProfile updated = studentProfileRepository.findById(studentProfile.getId()).orElseThrow();
        assertThat(updated.getStudentNo()).isEqualTo("2019123456");
        assertThat(updated.getCurrentGrade()).isEqualTo(4);
        assertThat(updated.getEnrollmentStatus()).isEqualTo("재학");
        assertThat(updated.getAdmissionYear()).isEqualTo(2019);
        int currentMonth = java.time.LocalDate.now().getMonthValue();
        assertThat(updated.getCurrentTerm()).isEqualTo((currentMonth >= 3 && currentMonth <= 8) ? 1 : 2);
    }

    @Test
    void 과목과_전공요건이_모두_비어있으면_500_TRANS_001을_반환한다() throws Exception {
        StudentProfile studentProfile = onboardedStudent("8008");
        when(pdfTranscriptParser.parse(any()))
                .thenReturn(new ParsedTranscript(Map.of(), Map.of(), List.of(), List.of(), List.of()));

        mockMvc.perform(multipart("/api/v1/diagnosis/upload")
                        .file(new MockMultipartFile("file", "transcript.pdf", "application/pdf", PDF_BYTES))
                        .with(authentication(authenticationOf(studentProfile.getMember().getId()))))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("TRANS_001"));
    }

    private List<StudentCourse> coursesOf(StudentProfile studentProfile) {
        return studentCourseRepository.findAll().stream()
                .filter(course -> studentProfile.getId().equals(course.getStudentProfile().getId()))
                .toList();
    }

    private Authentication authenticationOf(Long memberId) {
        return new UsernamePasswordAuthenticationToken(memberId.toString(), null, Collections.emptyList());
    }

    private ParsedTranscript sampleParsedTranscript() {
        Map<String, String> course = Map.of(
                "section", "자유이수",
                "courseCode", "GEC1104",
                "courseName", "World Citizen",
                "credits", "3",
                "semester", "2023/1"
        );
        return new ParsedTranscript(Map.of(), Map.of(), List.of(), List.of(), List.of(course));
    }
}
