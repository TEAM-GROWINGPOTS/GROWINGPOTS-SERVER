package com.growingpots.domain.transcript.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PdfTranscriptParserTest {

    private final PdfTranscriptParser parser = new PdfTranscriptParser();

    @TempDir
    Path tempDir;

    @Test
    void extractsStudentInfoFromPositionedText() throws Exception {
        Path pdfPath = tempDir.resolve("sample.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contentStream.newLineAtOffset(50, 700);
                contentStream.showText("studentId 2023103101");
                contentStream.endText();
            }

            document.save(pdfPath.toFile());
        }

        ParsedTranscript result = parser.parse(Files.readAllBytes(pdfPath));

        assertThat(result.courses()).isEmpty();
        assertThat(result.studentInfo()).isEmpty();
    }

    @Test
    void extractsSportsMedicineMajorSectionFromRealGraduationAuditPdf() throws Exception {
        Path pdfPath = Path.of("/Users/test/Desktop/광운대/3학년/동아리/sopt/growingpots/졸업관리표/스포츠의학과/24스포츠의학과_졸업사정관리표.pdf");
        assumeTrue(Files.exists(pdfPath));

        ParsedTranscript result = parser.parse(Files.readAllBytes(pdfPath));

        assertThat(result.studentInfo().get("department")).isEqualTo("스포츠의학과");
        assertThat(countInSection(result.courses(), "자유이수")).isEqualTo(3);
        assertThat(countInSection(result.courses(), "기타")).isEqualTo(2);
        assertThat(countInSection(result.courses(), "재수강")).isEqualTo(1);
        assertThat(countInSection(result.courses(), "금학기수강학점")).isEqualTo(6);
        assertThat(inSection(result.courses(), "자유이수"))
                .anySatisfy(course -> assertThat(course.get("courseName")).isEqualTo("전공탐색및기업가정신세미나"));
        // SW인증 필요/취득 학점이 판정(통과) 결과와 일치하는지 확인: 6학점 필요에 6학점 취득 → 통과
        assertThat(result.graduationSummary().get("swCertRequirement")).isEqualTo("6");
        assertThat(result.graduationSummary().get("swCertEarned")).isEqualTo("6");
        assertThat(result.graduationSummary().get("swCertification")).isEqualTo("통과");
        // 전공내역 표의 "기타 공통 일반선택 12" 블록 아래 "학점계: 12"에서 취득 학점을 가져온다.
        assertThat(result.graduationSummary().get("generalElectiveEarned")).isEqualTo("12");
    }

    @Test
    void extractsTheaterGraduationAuditPdfWithoutDroppingFiveDigitCourseCodes() throws Exception {
        Path pdfPath = Path.of("/Users/test/Desktop/광운대/3학년/동아리/sopt/growingpots/졸업관리표/연극영화과/23연극영화과_졸업사정관리표.pdf");
        assumeTrue(Files.exists(pdfPath));

        ParsedTranscript result = parser.parse(Files.readAllBytes(pdfPath));

        assertThat(result.studentInfo().get("department")).isEqualTo("연극영화학과");
        assertThat(countInSection(result.courses(), "배분이수")).isEqualTo(6);
        assertThat(countInSection(result.courses(), "자유이수")).isEqualTo(5);
        assertThat(countInSection(result.courses(), "기타")).isEqualTo(3);
        // 이 학과는 "연극영화학"/"영화트랙" 두 트랙 표에 같은 과목(같은 학기·학점)이 중복으로 나열되는데,
        // 실제 수강 이력은 한 번뿐이니 먼저 나온 "연극영화학" 쪽만 남고 "영화트랙" 중복은 전부 제거돼야 한다.
        assertThat(countInSection(result.courses(), "영화트랙")).isEqualTo(0);
        assertThat(countInSection(result.courses(), "연극영화학")).isEqualTo(15);
        assertThat(result.courses()).hasSize(41);
        assertThat(inSection(result.courses(), "배분이수"))
                .anySatisfy(course -> assertThat(course.get("courseCode")).isEqualTo("GED11020"))
                .anySatisfy(course -> assertThat(course.get("courseCode")).isEqualTo("GED11107"));
        // SW인증 필요/취득 학점이 판정(미통과) 결과와 일치하는지 확인: 6학점 필요에 4학점 취득 → 미통과
        assertThat(result.graduationSummary().get("swCertRequirement")).isEqualTo("6");
        assertThat(result.graduationSummary().get("swCertEarned")).isEqualTo("4");
        assertThat(result.graduationSummary().get("swCertification")).isEqualTo("미통과");
        // 전공내역 표의 "기타 공통 일반선택 8" 블록 아래 "학점계: 8"에서 취득 학점을 가져온다.
        assertThat(result.graduationSummary().get("generalElectiveEarned")).isEqualTo("8");
    }

    // "연극영화학"/"영화트랙" 두 트랙 표에 모두 나열되는 과목(FT2011 등)은 실제로는 한 번만 수강한
    // 것이므로, 파싱 결과에도 courseCode당 1개 row만 남아야 한다(section은 먼저 나온 "연극영화학").
    @Test
    void 두_트랙_표에_모두_나열된_과목은_한_번만_반영된다() throws Exception {
        Path pdfPath = Path.of("/Users/test/Desktop/광운대/3학년/동아리/sopt/growingpots/졸업관리표/연극영화과/23연극영화과_졸업사정관리표.pdf");
        assumeTrue(Files.exists(pdfPath));

        ParsedTranscript result = parser.parse(Files.readAllBytes(pdfPath));

        for (String duplicatedCode : List.of("FT2011", "FT2009", "FT3071", "FT2023", "FT2021")) {
            List<Map<String, String>> matches = result.courses().stream()
                    .filter(course -> duplicatedCode.equals(course.get("courseCode")))
                    .toList();
            assertThat(matches).as("courseCode=%s", duplicatedCode).hasSize(1);
            assertThat(matches.get(0).get("section")).isEqualTo("연극영화학");
        }
    }

    @Test
    void 이수구분코드는_빈줄에서_직전값을_이어받고_null이_없다() throws Exception {
        Path pdfPath = Path.of("/Users/test/Desktop/광운대/3학년/동아리/sopt/growingpots/졸업관리표/스포츠의학과/24스포츠의학과_졸업사정관리표.pdf");
        assumeTrue(Files.exists(pdfPath));

        ParsedTranscript result = parser.parse(Files.readAllBytes(pdfPath));

        assertThat(result.courses())
                .allSatisfy(course -> assertThat(course.get("rawClassification")).isNotNull());
    }

    @Test
    void 같은과목이_금학기수강학점과_일반목록에_동시에_있으면_금학기만_남고_재수강이력은_보존된다() throws Exception {
        Path pdfPath = Path.of("/Users/test/Desktop/광운대/3학년/동아리/sopt/growingpots/졸업관리표/스포츠의학과/24스포츠의학과_졸업사정관리표.pdf");
        assumeTrue(Files.exists(pdfPath));

        ParsedTranscript result = parser.parse(Files.readAllBytes(pdfPath));

        List<Map<String, String>> sm2011 = result.courses().stream()
                .filter(course -> "SM2011".equals(course.get("courseCode")))
                .toList();
        assertThat(sm2011).extracting(course -> course.get("section"))
                .containsExactlyInAnyOrder("재수강", "금학기수강학점");
        assertThat(sm2011).filteredOn(course -> "재수강".equals(course.get("section")))
                .anySatisfy(course -> assertThat(course.get("semester")).isEqualTo("2024/1"));
    }

    private long countInSection(List<Map<String, String>> courses, String section) {
        return inSection(courses, section).size();
    }

    private List<Map<String, String>> inSection(List<Map<String, String>> courses, String section) {
        return courses.stream()
                .filter(course -> section.equals(course.get("section")))
                .toList();
    }
}
