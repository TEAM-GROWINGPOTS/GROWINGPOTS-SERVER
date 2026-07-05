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
import com.growingpots.domain.transcript.entity.StudentCourse;
import com.growingpots.domain.transcript.parser.ParsedTranscript;
import com.growingpots.domain.transcript.parser.PdfParsingException;
import com.growingpots.domain.transcript.parser.PdfTranscriptParser;
import com.growingpots.domain.transcript.repository.StudentCourseRepository;
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

    @MockitoBean
    private PdfTranscriptParser pdfTranscriptParser;

    @Test
    void 신규_사용자는_파싱된_과목을_저장하고_201을_반환한다() throws Exception {
        Long memberId = 1001L;
        when(pdfTranscriptParser.parse(any())).thenReturn(sampleParsedTranscript());

        mockMvc.perform(multipart("/api/v1/diagnosis/upload")
                        .file(new MockMultipartFile("file", "transcript.pdf", "application/pdf", PDF_BYTES))
                        .with(authentication(authenticationOf(memberId))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("TRANS_201"));

        List<StudentCourse> saved = coursesOf(memberId);
        assertThat(saved).hasSize(1);
        StudentCourse course = saved.getFirst();
        assertThat(course.getRawCourseCode()).isEqualTo("GEC1104");
        assertThat(course.getTakenYear()).isEqualTo(2023);
        assertThat(course.getTakenSemester()).isEqualTo("1");
        assertThat(course.getStatus()).isEqualTo(CourseStatus.COMPLETED);
        assertThat(course.getSource()).isEqualTo(RecordSource.PDF);
        assertThat(course.isRetake()).isFalse();
    }

    @Test
    void 재업로드하면_기존_PDF_데이터는_삭제되고_수동입력_데이터는_보존된다() throws Exception {
        Long memberId = 2002L;
        studentCourseRepository.save(StudentCourse.builder()
                .memberId(memberId)
                .rawCourseCode("OLD001")
                .rawCourseName("옛날 PDF 과목")
                .credit(3)
                .takenYear(2020)
                .takenSemester("1")
                .section("기타")
                .status(CourseStatus.COMPLETED)
                .source(RecordSource.PDF)
                .build());
        studentCourseRepository.save(StudentCourse.builder()
                .memberId(memberId)
                .rawCourseCode("MAN001")
                .rawCourseName("수동으로 추가한 과목")
                .credit(2)
                .takenYear(2021)
                .takenSemester("2")
                .section("자유이수")
                .status(CourseStatus.COMPLETED)
                .source(RecordSource.MANUAL)
                .build());

        when(pdfTranscriptParser.parse(any())).thenReturn(sampleParsedTranscript());

        mockMvc.perform(multipart("/api/v1/diagnosis/upload")
                        .file(new MockMultipartFile("file", "transcript.pdf", "application/pdf", PDF_BYTES))
                        .with(authentication(authenticationOf(memberId))))
                .andExpect(status().isCreated());

        List<StudentCourse> saved = coursesOf(memberId);
        assertThat(saved).extracting(StudentCourse::getRawCourseCode)
                .containsExactlyInAnyOrder("MAN001", "GEC1104");
    }

    @Test
    void PDF가_아니면_400_TRANS_002를_반환한다() throws Exception {
        Long memberId = 3003L;

        mockMvc.perform(multipart("/api/v1/diagnosis/upload")
                        .file(new MockMultipartFile("file", "not.pdf", "application/pdf",
                                "이건 PDF가 아님".getBytes(StandardCharsets.UTF_8)))
                        .with(authentication(authenticationOf(memberId))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TRANS_002"));
    }

    @Test
    void 파싱에_실패하면_500_TRANS_001을_반환한다() throws Exception {
        Long memberId = 4004L;
        when(pdfTranscriptParser.parse(any())).thenThrow(new PdfParsingException("파싱 실패"));

        mockMvc.perform(multipart("/api/v1/diagnosis/upload")
                        .file(new MockMultipartFile("file", "transcript.pdf", "application/pdf", PDF_BYTES))
                        .with(authentication(authenticationOf(memberId))))
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
    void 금학기수강학점_과목은_수강년도_학기없이_IN_PROGRESS로_저장된다() throws Exception {
        Long memberId = 5005L;
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
                        .with(authentication(authenticationOf(memberId))))
                .andExpect(status().isCreated());

        StudentCourse course = coursesOf(memberId).getFirst();
        assertThat(course.getStatus()).isEqualTo(CourseStatus.IN_PROGRESS);
        assertThat(course.getTakenYear()).isNull();
        assertThat(course.getTakenSemester()).isNull();
    }

    private List<StudentCourse> coursesOf(Long memberId) {
        return studentCourseRepository.findAll().stream()
                .filter(course -> memberId.equals(course.getMemberId()))
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
