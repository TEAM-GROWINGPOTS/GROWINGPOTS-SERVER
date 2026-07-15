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
        // KHCU0045(경희사이버대 학점교류 과목)는 과목명 자체에 슬래시가 섞여 있어 년도/학기를 못 찾지만,
        // 조용히 빠지지 않고 "확인 필요" 상태(학점 0)로 남는다(#192 후속) - 옆 전공 과목(FT3039)은
        // 더 이상 통째로 삼켜지지 않고 정상 분리된다.
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
                .anySatisfy(course -> assertThat(course.get("courseCode")).isEqualTo("GED11107"))
                .anySatisfy(course -> assertThat(course.get("courseCode")).isEqualTo("KHCU0045"));
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

    // 금학기수강학점 줄은 실제 소속 section("08 기타" 표) 정보 없이 축약돼 있어서, 일반 목록 쪽의
    // 같은 과목코드에서 이수구분(section)을 이어받아야 한다(#188). 그렇지 않으면 rawClassification="04"만
    // 보고 전공필수로 오판정된다.
    @Test
    void 금학기수강학점_과목은_일반목록에서_실제_소속_section을_이어받는다() throws Exception {
        Path pdfPath = Path.of("/Users/test/Desktop/광운대/3학년/동아리/sopt/growingpots/졸업관리표/김경민_졸업사정관리표_260623.pdf");
        assumeTrue(Files.exists(pdfPath));

        ParsedTranscript result = parser.parse(Files.readAllBytes(pdfPath));

        List<Map<String, String>> fr3011 = result.courses().stream()
                .filter(course -> "FR3011".equals(course.get("courseCode")))
                .toList();
        assertThat(fr3011).hasSize(1);
        Map<String, String> course = fr3011.get(0);
        assertThat(course.get("section")).isEqualTo("금학기수강학점");
        assertThat(course.get("divisionSection")).isEqualTo("기타");
    }

    // PDF 폰트가 특정 글리프의 유니코드 매핑을 못 주면 PDFBox가 U+FFFD(REPLACEMENT CHARACTER, �)를
    // 대신 반환해서 과목명에 섞여 들어온다(예: "취�창업스쿨(진로의사결정을통한목표설정"). 그 글자
    // 자체를 복구할 방법은 없지만, 최소한 이상한 문자가 그대로 노출되진 않도록 제거한다(#242).
    @Test
    void 유니코드_매핑_실패_글리프는_과목명에서_제거된다() throws Exception {
        Path pdfPath = Path.of("/Users/test/Desktop/광운대/3학년/동아리/sopt/growingpots/졸업관리표/김경민_졸업사정관리표_260623.pdf");
        assumeTrue(Files.exists(pdfPath));

        ParsedTranscript result = parser.parse(Files.readAllBytes(pdfPath));

        String name = courseName(result.courses(), "CDG0268");
        assertThat(name).doesNotContain("�");
        assertThat(name).contains("창업스쿨");
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

    // 금학기수강학점 영역에서 과목코드와 과목명이 공백 없이 붙어 나오는 행("SM320운동손상평가"처럼)이
    // 있는데, 그중 과목명 끝자리가 숫자로 끝나는 과목("공학수학2")은 비탐욕 매칭이 그 숫자를 학점으로
    // 먼저 채가고 진짜 학점(3)을 트레일링 텍스트로 통째로 버리는 버그가 있었다(#210). 과목명 그룹과
    // 학점 숫자 그룹 사이에 실제 공백을 강제해서 고쳤다 — 회귀 방지용 테스트.
    @Test
    void 금학기수강학점_과목명이_숫자로_끝나도_학점을_정확히_구분한다() throws Exception {
        Path pdfPath = Path.of("/Users/test/Desktop/광운대/3학년/동아리/sopt/growingpots/졸업관리표/스포츠의학과/24스포츠의학과_졸업사정관리표.pdf");
        assumeTrue(Files.exists(pdfPath));

        ParsedTranscript result = parser.parse(Files.readAllBytes(pdfPath));

        List<Map<String, String>> bme204 = result.courses().stream()
                .filter(course -> "BME204".equals(course.get("courseCode")))
                .toList();
        assertThat(bme204).hasSize(1);
        assertThat(bme204.get(0).get("courseName")).isEqualTo("공학수학2");
        assertThat(bme204.get(0).get("credits")).isEqualTo("3");

        // 같은 영역의 다른 과목들(과목명이 숫자로 안 끝나는 경우)도 여전히 정확히 파싱돼야 한다.
        assertThat(countInSection(result.courses(), "금학기수강학점")).isEqualTo(6);
        assertThat(courseName(result.courses(), "SM320")).isEqualTo("운동손상평가");
        assertThat(result.courses().stream()
                        .filter(course -> "SM320".equals(course.get("courseCode")))
                        .findFirst().orElseThrow().get("credits"))
                .isEqualTo("3");
    }

    // 같은 줄(row)에서 왼쪽(교양) 과목명 세그먼트가 슬래시/괄호가 섞인 형태로 뭉쳐 나와(경희사이버대
    // 학점교류 과목 등) 왼쪽 과목 자체의 "년도/학기"를 못 찾으면, 정규식 기반 폴백 파서가 오른쪽(전공)
    // 컬럼의 실제 "년도/학기"까지 넘어가 버려서 그 사이에 있는 오른쪽 과목의 코드·이름·학점을 통째로
    // 왼쪽 과목명에 삼켜버리는 문제가 있었다(#192, 실제로 로컬 DB에서 손상된 row 발견). 왼쪽 과목명이
    // 이상해지는 것 자체는 막기 어렵지만(그 과목 고유의 형식 문제), 최소한 오른쪽 과목은 삼켜지지
    // 않고 자기 자신의 code/name/credit으로 정확히 파싱돼야 한다.
    @Test
    void 왼쪽_과목명이_슬래시를_포함해도_오른쪽_과목을_흡수하지_않는다() throws Exception {
        Path pdfPath = Path.of("/Users/test/Desktop/광운대/3학년/동아리/sopt/growingpots/졸업관리표/추가 pdf/컴퓨터공학과_21_신진수_졸업진단표.pdf");
        assumeTrue(Files.exists(pdfPath));

        ParsedTranscript result = parser.parse(Files.readAllBytes(pdfPath));

        // 오른쪽(전공) 과목은 왼쪽 과목의 이름에 흡수되지 않고 자기 자신의 정보로 정확히 파싱돼야 한다.
        List<Map<String, String>> aphy1002 = result.courses().stream()
                .filter(course -> "APHY1002".equals(course.get("courseCode")))
                .toList();
        assertThat(aphy1002).hasSize(1);
        assertThat(aphy1002.get(0).get("courseName")).isEqualTo("물리학및실험1");
        assertThat(aphy1002.get(0).get("credits")).isEqualTo("3");

        // 왼쪽(EXCH01713) 과목명 자체의 형식이 깨져서 학점/학기를 못 찾는 경우 그 행 자체는 버려지는데,
        // 적어도 다른 과목 코드가 섞여 들어간 "손상된" 형태로 저장되면 안 된다.
        assertThat(result.courses()).noneMatch(
                course -> String.valueOf(course.get("courseName")).contains("APHY1002"));
    }

    // 연극영화과 PDF에도 같은 패턴(경희사이버대 과목명에 슬래시 포함)이 있고, 그 옆에 있던 전공
    // 과목(FT3039/FT3081)이 예전엔 통째로 사라지거나 손상된 이름에 흡수됐었다.
    @Test
    void 연극영화과_PDF에서도_옆_전공과목이_정상적으로_파싱된다() throws Exception {
        Path pdfPath = Path.of("/Users/test/Desktop/광운대/3학년/동아리/sopt/growingpots/졸업관리표/연극영화과/23연극영화과_졸업사정관리표.pdf");
        assumeTrue(Files.exists(pdfPath));

        ParsedTranscript result = parser.parse(Files.readAllBytes(pdfPath));

        assertThat(courseName(result.courses(), "FT3039")).isEqualTo("영화편집연구");
        assertThat(courseName(result.courses(), "FT3081")).isEqualTo("고급시나리오창작");
        assertThat(result.courses()).noneMatch(course -> String.valueOf(course.get("courseName")).contains("FT30"));
    }

    // KHCU0045(경희사이버대 학점교류 과목)처럼 옆 과목을 삼키던 형태로 깨진 과목은, 학점/학기를
    // 알아볼 수 없어도 조용히 빠지지 않고 "확인 필요" 상태(학점 0, 학기 없음)로라도 남아야 한다(#192
    // 후속) - 검수 화면에서 사용자가 직접 고칠 수 있게. 옆 전공 과목(FT3039)은 그대로 정상 분리된다.
    @Test
    void 옆_과목을_삼키던_과목도_확인_필요_상태로_남는다() throws Exception {
        Path pdfPath = Path.of("/Users/test/Desktop/광운대/3학년/동아리/sopt/growingpots/졸업관리표/연극영화과/23연극영화과_졸업사정관리표.pdf");
        assumeTrue(Files.exists(pdfPath));

        ParsedTranscript result = parser.parse(Files.readAllBytes(pdfPath));

        List<Map<String, String>> khcu0045 = result.courses().stream()
                .filter(course -> "KHCU0045".equals(course.get("courseCode")))
                .toList();
        assertThat(khcu0045).hasSize(1);
        assertThat(khcu0045.get(0).get("courseName")).contains("여행을통한인간삶의가치증진");
        assertThat(khcu0045.get(0).get("credits")).isEqualTo("0");
        assertThat(khcu0045.get(0).get("section")).isEqualTo("배분이수");

        assertThat(courseName(result.courses(), "FT3039")).isEqualTo("영화편집연구");
        assertThat(result.courses().stream()
                        .filter(course -> "FT3039".equals(course.get("courseCode")))
                        .findFirst().orElseThrow().get("credits"))
                .isEqualTo("3");
    }

    // 같은 패턴(EXCH01713)이 다른 학과 PDF에도 있다 - 학점교류 과목 전반에 적용되는지 확인.
    @Test
    void 다른_PDF에서도_학점교류_과목이_확인_필요_상태로_남고_옆_과목은_정상_분리된다() throws Exception {
        Path pdfPath = Path.of("/Users/test/Desktop/광운대/3학년/동아리/sopt/growingpots/졸업관리표/추가 pdf/컴퓨터공학과_21_신진수_졸업진단표.pdf");
        assumeTrue(Files.exists(pdfPath));

        ParsedTranscript result = parser.parse(Files.readAllBytes(pdfPath));

        List<Map<String, String>> exch = result.courses().stream()
                .filter(course -> "EXCH01713".equals(course.get("courseCode")))
                .toList();
        assertThat(exch).hasSize(1);
        assertThat(exch.get(0).get("courseName")).contains("로컬콘텐츠실감미디어");
        assertThat(exch.get(0).get("credits")).isEqualTo("0");

        assertThat(courseName(result.courses(), "APHY1002")).isEqualTo("물리학및실험1");
        assertThat(result.courses().stream()
                        .filter(course -> "APHY1002".equals(course.get("courseCode")))
                        .findFirst().orElseThrow().get("credits"))
                .isEqualTo("3");
    }
}
