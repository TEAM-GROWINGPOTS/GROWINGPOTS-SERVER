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

    // 범례 마커(e:영어강의, *:학점교류과목 등)가 좌표 겹침으로 과목명 앞에 붙어 나오는 문제(#172) 검증.
    @Test
    void 과목명_앞에_붙은_범례_마커가_제거된다() throws Exception {
        Path pdfPath = Path.of("/Users/test/Desktop/광운대/3학년/동아리/sopt/growingpots/졸업관리표/추가 pdf/도예학과_23_고찬란_졸업진단표.pdf");
        assumeTrue(Files.exists(pdfPath));

        ParsedTranscript result = parser.parse(Files.readAllBytes(pdfPath));

        assertThat(courseName(result.courses(), "CA2004")).isEqualTo("혼합매체연구");
        assertThat(courseName(result.courses(), "CA2013")).isEqualTo("3D디지털모델링");
        assertThat(courseName(result.courses(), "CA3017")).isEqualTo("도자제품브랜드");
        assertThat(courseName(result.courses(), "CA4002")).isEqualTo("SeniorProject I-B");
        assertThat(courseName(result.courses(), "CA3025")).isEqualTo("전공연수(도예학)");
        // 진짜 대문자로 시작하는 과목명은 마커로 오인해 잘리면 안 된다.
        assertThat(courseName(result.courses(), "CA4001")).isEqualTo("SeniorProject I-A");
    }

    // 마커가 2개 이상 연달아 붙는 경우(예: s:SW인증 + e:영어강의)도 전부 제거돼야 한다.
    @Test
    void 연속으로_붙은_범례_마커도_전부_제거된다() throws Exception {
        Path pdfPath = Path.of("/Users/test/Desktop/광운대/3학년/동아리/sopt/growingpots/졸업관리표/추가 pdf/컴퓨터공학과_21_신진수_졸업진단표.pdf");
        assumeTrue(Files.exists(pdfPath));

        ParsedTranscript result = parser.parse(Files.readAllBytes(pdfPath));

        assertThat(courseName(result.courses(), "SWCON104")).isEqualTo("웹/파이선프로그래밍");
    }

    // 숫자(교직기본이수분야) 마커는 과목명과 별도 세그먼트(공백으로 분리)로 붙어 나오므로 걸러내야 하고,
    // "조경설계1"처럼 진짜 과목명 끝에 붙는 일련번호(이름과 한 세그먼트)는 그대로 보존돼야 한다.
    @Test
    void 숫자_마커는_제거되고_진짜_과목명_끝의_일련번호는_보존된다() throws Exception {
        Path pdfPath = Path.of("/Users/test/Desktop/광운대/3학년/동아리/sopt/growingpots/졸업관리표/추가 pdf/환경조경디자인학과_23_최서진_졸업진단표.pdf");
        assumeTrue(Files.exists(pdfPath));

        ParsedTranscript result = parser.parse(Files.readAllBytes(pdfPath));

        assertThat(courseName(result.courses(), "LA211")).isEqualTo("환경생태계획론");
        assertThat(courseName(result.courses(), "LA102")).isEqualTo("조경계획학");
        assertThat(courseName(result.courses(), "LA216")).isEqualTo("조경수목학");
        assertThat(courseName(result.courses(), "LA336")).isEqualTo("환경심리행태론");
        assertThat(courseName(result.courses(), "LA338")).isEqualTo("식재계획및설계");
        assertThat(courseName(result.courses(), "LA213")).isEqualTo("조경설계1");
        assertThat(courseName(result.courses(), "LA332")).isEqualTo("조경설계3");
    }

    private String courseName(List<Map<String, String>> courses, String courseCode) {
        return courses.stream()
                .filter(course -> courseCode.equals(course.get("courseCode")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("과목을 찾을 수 없음: " + courseCode))
                .get("courseName");
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
