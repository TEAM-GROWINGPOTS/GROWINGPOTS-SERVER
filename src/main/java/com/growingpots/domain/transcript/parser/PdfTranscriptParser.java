package com.growingpots.domain.transcript.parser;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.stereotype.Component;

// 졸업사정관리표 PDF에서 학생정보/졸업요건요약/교양이수/전공요건/이수과목을 텍스트 좌표 기반으로 추출한다.
@Component
public class PdfTranscriptParser {

    private static final float SAME_LINE_Y_TOLERANCE = 2.5F;
    private static final float SEGMENT_GAP_RATIO = 0.8F;
    private static final Pattern STUDENT_INFO_PATTERN = Pattern.compile(
            "학\\s*번\\s+(\\S+)\\s+성\\s*명\\s+(\\S+)\\s+학\\s*과\\s+(\\S+)\\s+학\\s*년\\s+(\\S+)\\s+등?\\s*록\\s*횟\\s*수\\s+(\\S+)\\s+학적\\s*상태\\s+(\\S+)"
    );
    private static final Pattern ADMISSION_INFO_PATTERN = Pattern.compile(
            "입 학 구 분\\s+(\\S+)\\s+졸업기준학점\\(년도\\)\\s+(\\S+).*최종판정\\s+(\\S+)"
    );
    private static final Pattern SUMMARY_CRITERIA_PATTERN = Pattern.compile(
            "기준\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)"
    );
    // "취득" 줄은 "기준"/"판정" 줄과 달리 컬럼이 하나 적다(11개). 마지막 그룹(11번째)이 SW인증 취득 학점이다.
    private static final Pattern SUMMARY_EARNED_PATTERN = Pattern.compile(
            "취득\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)"
    );
    private static final Pattern SUMMARY_JUDGEMENT_PATTERN = Pattern.compile(
            "판정\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)"
    );
    private static final Pattern GENERAL_EDUCATION_BOTH_PATTERN = Pattern.compile(
            "(.+?)\\s+(\\d+)\\s*/\\s*(\\d+)\\s+(\\d+)\\s*/\\s*(\\d+)\\s+(통과|미통과)\\s+(.+?)\\s+(\\d+)\\s*/\\s*(\\d+)\\s+(\\d+)\\s*/\\s*(\\d+)\\s+(통과|미통과)"
    );
    private static final Pattern GENERAL_EDUCATION_SINGLE_PATTERN = Pattern.compile(
            "(.+?)\\s+(\\d+)\\s*/\\s*(\\d+)\\s+(\\d+)\\s*/\\s*(\\d+)\\s+(통과|미통과)"
    );
    private static final Pattern MAJOR_REQUIREMENT_PATTERN = Pattern.compile(
            "(심화전공|단일전공|복수전공|다전공)\\s+(\\d+)\\s+(.+?)\\s+(\\d{4})\\s+"
                    + "(\\d+)\\s*/\\s*(\\d+)\\s+(\\d+)\\s*/\\s*(\\d+)\\s+(\\d+)\\s*/\\s*(\\d+)\\s+"
                    + "(\\d+)\\s*/\\s*(\\d+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)"
    );
    private static final Pattern COURSE_CODE_PATTERN = Pattern.compile("[A-Z]{2,5}\\d{3,5}");
    private static final Pattern CURRENT_COURSE_PATTERN = Pattern.compile(
            "^\\s*(\\d{2})\\s+([A-Z]{2,5}\\d{3,5})\\s*(.+?)\\s*(\\d)(?:\\s+.*)?$"
    );
    private static final Pattern COMPLETED_COURSE_LINE_PATTERN = Pattern.compile(
            "^\\s*(\\d{2,4})(?:\\s+\\d{2})?\\s+([A-Z]{2,5}\\d{3,5})\\s*(.+?)\\s*(\\d{4}\\s*/\\s*\\d)(?:\\s+.*)?$"
    );
    private static final Pattern SEMESTER_PATTERN = Pattern.compile("\\d{4}\\s*/\\s*\\d");
    private static final Pattern MAJOR_CODE_PATTERN = Pattern.compile("A\\s*\\d{5}");
    private static final Pattern MAJOR_HEADER_PATTERN = Pattern.compile("\\bA\\s*\\d{5}\\s*(.+?)\\s+(?:미취득|취득|중복과목|\\d전공)");
    private static final Pattern MAJOR_HEADER_SEGMENT_PATTERN = Pattern.compile("A\\s*\\d{5}\\s*(.+?)(?:\\s+(?:미취득|취득|중복과목|\\d전공).*)?");
    private static final Pattern EMBEDDED_KOREAN_CREDIT_PATTERN = Pattern.compile("(?<=[가-힣])([1-9])(?=[가-힣])");

    public ParsedTranscript parse(byte[] pdfBytes) {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PositionCollectingTextStripper stripper = new PositionCollectingTextStripper();
            stripper.setSortByPosition(true);
            stripper.writeText(document, new StringWriter());

            List<PageText> pageTexts = stripper.toPageTexts();
            return toParsedTranscript(pageTexts);
        } catch (IOException e) {
            throw new PdfParsingException("PDF 파싱에 실패했습니다.", e);
        }
    }

    private ParsedTranscript toParsedTranscript(List<PageText> pageTexts) {
        List<String> lines = pageTexts.stream()
                .flatMap(pageText -> pageText.lines().stream())
                .map(TextLine::text)
                .toList();

        List<Map<String, String>> majorRequirements = extractMajorRequirements(lines);
        List<Map<String, String>> regularCourses = extractCourses(pageTexts, defaultMajorSection(majorRequirements));
        List<Map<String, String>> currentSemesterCourses = extractCurrentSemesterCourses(pageTexts);
        List<Map<String, String>> courses = mergeCourses(regularCourses, currentSemesterCourses);

        return new ParsedTranscript(
                extractStudentInfo(lines),
                extractGraduationSummary(lines),
                extractGeneralEducation(lines),
                majorRequirements,
                courses
        );
    }

    // 같은 과목코드가 일반 목록과 금학기수강학점에 동시에 나오면 같은 사건(현재 재수강 중)이므로
    // 금학기수강학점 쪽만 남긴다. 단, 일반 목록에서 이미 "재수강"으로 표시된 건 과거의 별도 이력이라 그대로 유지
    private List<Map<String, String>> mergeCourses(
            List<Map<String, String>> regularCourses,
            List<Map<String, String>> currentSemesterCourses
    ) {
        Set<String> currentSemesterCourseCodes = currentSemesterCourses.stream()
                .map(course -> course.get("courseCode"))
                .collect(Collectors.toSet());

        List<Map<String, String>> merged = new ArrayList<>(regularCourses.stream()
                .filter(course -> !currentSemesterCourseCodes.contains(course.get("courseCode"))
                        || "재수강".equals(course.get("section")))
                .toList());
        merged.addAll(currentSemesterCourses);
        return merged;
    }

    private String defaultMajorSection(List<Map<String, String>> majorRequirements) {
        return majorRequirements.stream()
                .map(requirement -> requirement.get("majorName"))
                .filter(majorName -> majorName != null && !majorName.isBlank())
                .findFirst()
                .orElse("");
    }

    private Map<String, String> extractStudentInfo(List<String> lines) {
        Map<String, String> studentInfo = new LinkedHashMap<>();
        for (String line : lines) {
            String normalizedLine = normalizeStudentInfoLabels(line);
            if (normalizedLine.contains("학번") && normalizedLine.contains("성명")
                    && normalizedLine.contains("학과") && normalizedLine.contains("학년")
                    && normalizedLine.contains("등록횟수") && normalizedLine.contains("학적상태")) {
                studentInfo.put("studentId", valueBetween(normalizedLine, "학번", "성명"));
                studentInfo.put("name", valueBetween(normalizedLine, "성명", "학과"));
                studentInfo.put("department", valueBetween(normalizedLine, "학과", "학년"));
                studentInfo.put("grade", valueBetween(normalizedLine, "학년", "등록횟수"));
                studentInfo.put("registeredSemesters", valueBetween(normalizedLine, "등록횟수", "학적상태"));
                studentInfo.put("academicStatus", valueAfter(normalizedLine, "학적상태"));
                break;
            }
        }

        for (String line : lines) {
            Matcher matcher = ADMISSION_INFO_PATTERN.matcher(line);
            if (matcher.find()) {
                studentInfo.put("admissionType", matcher.group(1));
                studentInfo.put("graduationStandardYear", matcher.group(2));
                studentInfo.put("finalJudgement", matcher.group(3));
                break;
            }
        }
        return studentInfo;
    }

    private String normalizeStudentInfoLabels(String line) {
        return line.replaceAll("학\\s*번", "학번")
                .replaceAll("성\\s*명", "성명")
                .replaceAll("학\\s*과", "학과")
                .replaceAll("학\\s*년", "학년")
                .replaceAll("등\\s*록\\s*횟\\s*수", "등록횟수")
                .replaceAll("등록\\s*횟\\s*수", "등록횟수")
                .replaceAll("학적\\s*상태", "학적상태");
    }

    private String valueBetween(String line, String startLabel, String endLabel) {
        int start = line.indexOf(startLabel);
        int end = line.indexOf(endLabel);
        if (start < 0 || end < 0 || start + startLabel.length() > end) {
            return "";
        }
        return line.substring(start + startLabel.length(), end).trim();
    }

    private String valueAfter(String line, String label) {
        int start = line.indexOf(label);
        if (start < 0) {
            return "";
        }
        return line.substring(start + label.length()).trim();
    }

    private Map<String, String> extractGraduationSummary(List<String> lines) {
        Map<String, String> summary = new LinkedHashMap<>();
        String criteriaLine = findLineStartingWith(lines, "기준 ");
        String earnedLine = findLineStartingWith(lines, "취득 ");
        String judgementLine = findLineStartingWith(lines, "판정 ");

        Matcher criteria = SUMMARY_CRITERIA_PATTERN.matcher(criteriaLine);
        Matcher earned = SUMMARY_EARNED_PATTERN.matcher(earnedLine);
        Matcher judgement = SUMMARY_JUDGEMENT_PATTERN.matcher(judgementLine);
        if (criteria.find() && earned.find() && judgement.find()) {
            summary.put("requiredCredits", criteria.group(2));
            summary.put("earnedCredits", earned.group(2));
            summary.put("inProgressCredits", earned.group(1));
            summary.put("majorRequirement", criteria.group(3));
            summary.put("majorEarned", earned.group(3));
            summary.put("generalRequirement", criteria.group(4));
            summary.put("generalEarned", earned.group(4));
            summary.put("gpaRequirement", criteria.group(5));
            summary.put("gpaEarned", earned.group(5));
            summary.put("englishLectureRequirement", criteria.group(6));
            summary.put("englishLectureEarned", earned.group(6));
            summary.put("thesisRequirement", criteria.group(7));
            summary.put("thesisEarned", earned.group(7));
            summary.put("topik", criteria.group(8));
            summary.put("swCertRequirement", criteria.group(12));
            summary.put("swCertEarned", earned.group(11));
            summary.put("englishLectureJudgement", judgement.group(6));
            summary.put("thesisJudgement", judgement.group(7));
            summary.put("graduationCertification", judgement.group(10));
            summary.put("swCertification", judgement.group(12));
        }
        return summary;
    }

    private List<Map<String, String>> extractGeneralEducation(List<String> lines) {
        List<Map<String, String>> generalEducation = new ArrayList<>();
        int start = indexOf(lines, "교양내역");
        int end = indexOf(lines, "전공내역");
        if (start < 0 || end < 0) {
            return generalEducation;
        }

        for (String line : lines.subList(start + 1, end)) {
            Matcher bothMatcher = GENERAL_EDUCATION_BOTH_PATTERN.matcher(line);
            if (bothMatcher.find()) {
                generalEducation.add(toGeneralEducationRow(
                        bothMatcher.group(1), bothMatcher.group(2), bothMatcher.group(3),
                        bothMatcher.group(4), bothMatcher.group(5), bothMatcher.group(6)
                ));
                generalEducation.add(toGeneralEducationRow(
                        bothMatcher.group(7), bothMatcher.group(8), bothMatcher.group(9),
                        bothMatcher.group(10), bothMatcher.group(11), bothMatcher.group(12)
                ));
                continue;
            }

            Matcher singleMatcher = GENERAL_EDUCATION_SINGLE_PATTERN.matcher(line);
            if (singleMatcher.find() && !line.startsWith("이수구분")) {
                generalEducation.add(toGeneralEducationRow(
                        singleMatcher.group(1), singleMatcher.group(2), singleMatcher.group(3),
                        singleMatcher.group(4), singleMatcher.group(5), singleMatcher.group(6)
                ));
            }
        }
        return generalEducation;
    }

    private Map<String, String> toGeneralEducationRow(
            String category,
            String areaEarned,
            String areaRequired,
            String creditsEarned,
            String creditsRequired,
            String judgement
    ) {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("category", category.trim());
        row.put("areaEarned", areaEarned);
        row.put("areaRequired", areaRequired);
        row.put("creditsEarned", creditsEarned);
        row.put("creditsRequired", creditsRequired);
        row.put("judgement", judgement);
        return row;
    }

    private List<Map<String, String>> extractMajorRequirements(List<String> lines) {
        List<Map<String, String>> majorRequirements = new ArrayList<>();
        int start = indexOf(lines, "전공내역");
        int end = indexOf(lines, "교양/기타/금학기수강학점");
        if (start < 0 || end < 0) {
            return majorRequirements;
        }

        for (String line : lines.subList(start + 1, end)) {
            Matcher matcher = MAJOR_REQUIREMENT_PATTERN.matcher(line);
            if (matcher.find()) {
                Map<String, String> row = new LinkedHashMap<>();
                row.put("majorType", matcher.group(1));
                row.put("majorSequence", matcher.group(2));
                row.put("majorName", matcher.group(3).trim());
                row.put("standardYear", matcher.group(4));
                row.put("basicEarned", matcher.group(5));
                row.put("basicRequired", matcher.group(6));
                row.put("requiredEarned", matcher.group(7));
                row.put("requiredRequired", matcher.group(8));
                row.put("electiveEarned", matcher.group(9));
                row.put("electiveRequired", matcher.group(10));
                row.put("requiredPlusElectiveEarned", matcher.group(11));
                row.put("requiredPlusElectiveRequired", matcher.group(12));
                row.put("thesis", matcher.group(13));
                row.put("teaching", matcher.group(14));
                row.put("engineeringCertification", matcher.group(15));
                row.put("graduationRequired", matcher.group(16));
                row.put("manualChange", matcher.group(17));
                row.put("judgement", matcher.group(18));
                majorRequirements.add(row);
            }
        }
        return majorRequirements;
    }

    private List<Map<String, String>> extractCourses(List<PageText> pageTexts, String defaultRightSection) {
        List<Map<String, String>> courses = new ArrayList<>();
        CourseSectionState state = new CourseSectionState(defaultRightSection);
        boolean inCourseArea = false;
        boolean inCurrentSemesterArea = false;

        for (TextLine line : flattenTextLines(pageTexts)) {
            if (line.text().contains("교양/기타/금학기수강학점")) {
                inCourseArea = true;
                continue;
            }
            if (!inCourseArea) {
                continue;
            }
            if (line.text().contains("[금학기수강학점]")) {
                inCurrentSemesterArea = true;
                continue;
            }
            updateCourseSectionState(line, state);
            if (inCurrentSemesterArea) {
                // 이 구간의 과목은 extractCurrentSemesterCourses가 이미 "금학기수강학점"으로 전담 추출한다.
                // 여기서 또 추가하면 이전 전공/구분명이 붙은 채로 중복 저장된다.
                continue;
            }
            Optional<Map<String, String>> leftCourse =
                    parseCourseFromSide(line, CourseSide.LEFT, state.leftSection(), state);
            if (leftCourse.isPresent()) {
                courses.add(leftCourse.get());
            } else if (hasCourseCodeOnSide(line, CourseSide.LEFT)) {
                parseCompletedCourseFromLine(line, state.leftSection()).ifPresent(courses::add);
            }
            parseCourseFromSide(line, CourseSide.RIGHT, state.rightSection(), state)
                    .ifPresent(courses::add);
        }
        return courses;
    }

    private boolean hasCourseCodeOnSide(TextLine line, CourseSide side) {
        return line.segments().stream()
                .filter(segment -> side.contains(segment, line))
                .anyMatch(segment -> COURSE_CODE_PATTERN.matcher(segment.text()).find());
    }

    private List<Map<String, String>> extractCurrentSemesterCourses(List<PageText> pageTexts) {
        List<Map<String, String>> currentSemesterCourses = new ArrayList<>();
        boolean inCurrentSemesterArea = false;

        for (TextLine line : flattenTextLines(pageTexts)) {
            if (line.text().contains("[금학기수강학점]")) {
                inCurrentSemesterArea = true;
                continue;
            }
            if (!inCurrentSemesterArea) {
                continue;
            }
            if (line.text().contains("졸업능력인증")) {
                break;
            }
            parseCurrentSemesterCourse(line).ifPresent(currentSemesterCourses::add);
        }
        return currentSemesterCourses;
    }

    private void updateCourseSectionState(TextLine line, CourseSectionState state) {
        Matcher majorHeaderMatcher = MAJOR_HEADER_PATTERN.matcher(line.text());
        if (majorHeaderMatcher.find()) {
            state.rightSection(cleanMajorSectionName(majorHeaderMatcher.group(1)));
        }

        // 이수구분 코드는 세로로 병합된 셀처럼 표시되어, 빈 줄에서는 위에서 마지막으로 본 값을 그대로 이어받는다.
        for (CourseSide side : CourseSide.values()) {
            TextSegment rawClassificationSegment = findRawClassificationSegment(line.segments(), side);
            if (rawClassificationSegment != null) {
                state.rawClassification(side, rawClassificationSegment.text());
            }
        }

        for (int index = 0; index < line.segments().size(); index++) {
            TextSegment segment = line.segments().get(index);
            if (segment.x1() < 230) {
                String section = leftSectionName(segment.text());
                if (section != null) {
                    state.leftSection(section);
                }
            }

            Matcher majorHeaderSegmentMatcher = MAJOR_HEADER_SEGMENT_PATTERN.matcher(segment.text());
            if (segment.x1() >= 190 && majorHeaderSegmentMatcher.matches()) {
                state.rightSection(cleanMajorSectionName(majorHeaderSegmentMatcher.group(1)));
                continue;
            }

            if (segment.x1() >= 190 && segment.x1() < 330
                    && MAJOR_CODE_PATTERN.matcher(segment.text()).matches()
                    && index + 1 < line.segments().size()) {
                TextSegment nextSegment = line.segments().get(index + 1);
                if (nextSegment.x1() >= 220 && nextSegment.x1() < 430) {
                    state.rightSection(cleanMajorSectionName(nextSegment.text()));
                }
            }
        }
    }

    private String cleanMajorSectionName(String sectionName) {
        if (sectionName == null) {
            return "";
        }
        return sectionName
                .replaceAll("\\s+(?:미취득|취득|중복과목|\\d전공).*$", "")
                .trim();
    }

    private Optional<Map<String, String>> parseCourseFromSide(
            TextLine line,
            CourseSide side,
            String section,
            CourseSectionState state
    ) {
        List<TextSegment> segments = line.segments().stream()
                .filter(segment -> side.contains(segment, line))
                .toList();
        TextSegment codeSegment = findFirstMatchingSegment(segments, COURSE_CODE_PATTERN);
        if (codeSegment == null) {
            return Optional.empty();
        }

        TextSegment semesterSegment = findSemesterSegment(segments, side);
        if (semesterSegment == null) {
            return parseCourseWithBrokenSemester(line, segments, codeSegment, side, section, state);
        }

        TextSegment creditSegment = findCreditSegment(segments, side);
        String courseName = extractCourseName(segments, codeSegment, creditSegment, semesterSegment);
        String credits = creditSegment == null ? trailingCredit(courseName) : creditSegment.text();
        if (creditSegment == null && credits != null) {
            courseName = courseName.substring(0, courseName.length() - credits.length()).trim();
        }
        if (creditSegment == null && credits == null) {
            EmbeddedCredit embeddedCredit = embeddedCredit(courseName);
            if (embeddedCredit != null) {
                courseName = embeddedCredit.courseName();
                credits = embeddedCredit.credits();
            }
        }
        if ((courseName.isBlank() || credits == null || credits.isBlank()) && side == CourseSide.LEFT) {
            Optional<Map<String, String>> fallbackCourse =
                    parseCompletedCourseFromLine(line, section);
            if (fallbackCourse.isPresent()) {
                return fallbackCourse;
            }
        }

        if (courseName.isBlank() || credits == null || credits.isBlank()) {
            return Optional.empty();
        }

        String rawClassificationText = carriedRawClassification(segments, side, state);
        Map<String, String> course = new LinkedHashMap<>();
        course.put("section", courseSection(section, rawClassificationText, side));
        course.put("courseCode", firstMatch(codeSegment.text(), COURSE_CODE_PATTERN));
        course.put("courseName", courseName);
        course.put("credits", credits);
        course.put("semester", semesterSegment.text().replaceAll("\\s+", ""));

        if (rawClassificationText != null) {
            course.put("rawClassification", rawClassificationText);
        }
        return Optional.of(course);
    }

    private Optional<Map<String, String>> parseCourseWithBrokenSemester(
            TextLine line,
            List<TextSegment> segments,
            TextSegment codeSegment,
            CourseSide side,
            String section,
            CourseSectionState state
    ) {
        TextSegment slashSegment = findBrokenSemesterSlashSegment(segments, side);
        TextSegment semesterNumberSegment = slashSegment == null
                ? null
                : findBrokenSemesterNumberSegment(segments, slashSegment, side);
        if (slashSegment == null || semesterNumberSegment == null) {
            return parseCourseWithBrokenSemesterFromLine(line, codeSegment, side, section, state);
        }

        String rawCourseName = extractCourseNameUntilSlash(segments, codeSegment, slashSegment);
        String year = lastDigits(rawCourseName, 4);
        if (year == null) {
            return Optional.empty();
        }

        String courseName = cleanRecoveredCourseName(removeLastDigits(rawCourseName, 4));
        String credits = trailingCredit(courseName);
        if (credits != null) {
            courseName = courseName.substring(0, courseName.length() - credits.length()).trim();
        } else {
            EmbeddedCredit embeddedCredit = embeddedCredit(courseName);
            if (embeddedCredit == null) {
                return Optional.empty();
            }
            courseName = embeddedCredit.courseName();
            credits = embeddedCredit.credits();
        }

        if (courseName.isBlank()) {
            return Optional.empty();
        }

        String rawClassificationText = carriedRawClassification(segments, side, state);
        Map<String, String> course = new LinkedHashMap<>();
        course.put("section", courseSection(section, rawClassificationText, side));
        course.put("courseCode", firstMatch(codeSegment.text(), COURSE_CODE_PATTERN));
        course.put("courseName", courseName);
        course.put("credits", credits);
        course.put("semester", year + "/" + semesterNumberSegment.text());
        if (rawClassificationText != null) {
            course.put("rawClassification", rawClassificationText);
        }
        return Optional.of(course);
    }

    private Optional<Map<String, String>> parseCourseWithBrokenSemesterFromLine(
            TextLine line,
            TextSegment codeSegment,
            CourseSide side,
            String section,
            CourseSectionState state
    ) {
        if (side != CourseSide.LEFT) {
            return Optional.empty();
        }

        String courseCode = firstMatch(codeSegment.text(), COURSE_CODE_PATTERN);
        int codeIndex = line.text().indexOf(courseCode);
        int slashIndex = line.text().indexOf("/", codeIndex);
        if (codeIndex < 0 || slashIndex < 0) {
            return Optional.empty();
        }

        Matcher semesterNumberMatcher = Pattern.compile("/\\D*([1-4])").matcher(line.text().substring(slashIndex));
        if (!semesterNumberMatcher.find()) {
            return Optional.empty();
        }

        String rawCourseName = line.text().substring(codeIndex + courseCode.length(), slashIndex).trim();
        String year = lastDigits(rawCourseName, 4);
        if (year == null) {
            return Optional.empty();
        }

        String courseName = removeLastDigits(rawCourseName, 4).trim();
        String credits = trailingCredit(courseName);
        if (credits != null) {
            courseName = courseName.substring(0, courseName.length() - credits.length()).trim();
        } else {
            EmbeddedCredit embeddedCredit = embeddedCredit(courseName);
            if (embeddedCredit == null) {
                return Optional.empty();
            }
            courseName = embeddedCredit.courseName();
            credits = embeddedCredit.credits();
        }

        if (courseName.isBlank()) {
            return Optional.empty();
        }

        String rawClassificationText = carriedRawClassification(line.segments(), side, state);
        Map<String, String> course = new LinkedHashMap<>();
        course.put("section", courseSection(section, rawClassificationText, side));
        course.put("courseCode", courseCode);
        course.put("courseName", courseName);
        course.put("credits", credits);
        course.put("semester", year + "/" + semesterNumberMatcher.group(1));
        if (rawClassificationText != null) {
            course.put("rawClassification", rawClassificationText);
        }
        return Optional.of(course);
    }

    private String courseSection(String section, String rawClassificationText, CourseSide side) {
        if (side == CourseSide.LEFT && "04".equals(rawClassificationText)) {
            return "재수강";
        }
        return section == null ? "" : section;
    }

    // 세로 병합 셀 취급: 이 줄에서 이수구분 코드를 못 찾으면 위에서 마지막으로 본 값을 이어받는다.
    private String carriedRawClassification(List<TextSegment> segments, CourseSide side, CourseSectionState state) {
        TextSegment segment = findRawClassificationSegment(segments, side);
        return segment != null ? segment.text() : state.rawClassification(side);
    }

    private Optional<Map<String, String>> parseCompletedCourseFromLine(TextLine line, String section) {
        Matcher matcher = COMPLETED_COURSE_LINE_PATTERN.matcher(line.text());
        if (!matcher.find()) {
            return Optional.empty();
        }

        String rawClassification = matcher.group(1);
        if ("11".equals(rawClassification)) {
            return Optional.empty();
        }
        String courseName = matcher.group(3).trim();
        String credits = trailingCredit(courseName);
        if (credits != null) {
            courseName = courseName.substring(0, courseName.length() - credits.length()).trim();
        } else {
            EmbeddedCredit embeddedCredit = embeddedCredit(courseName);
            if (embeddedCredit == null) {
                return Optional.empty();
            }
            courseName = embeddedCredit.courseName();
            credits = embeddedCredit.credits();
        }

        Map<String, String> course = new LinkedHashMap<>();
        course.put("section", "04".equals(rawClassification) ? "재수강" : section);
        course.put("courseCode", matcher.group(2));
        course.put("courseName", courseName);
        course.put("credits", credits);
        course.put("semester", matcher.group(4).replaceAll("\\s+", ""));
        course.put("rawClassification", rawClassification);
        return Optional.of(course);
    }

    private Optional<Map<String, String>> parseCurrentSemesterCourse(TextLine line) {
        Matcher matcher = CURRENT_COURSE_PATTERN.matcher(line.text());
        if (!matcher.find()) {
            return Optional.empty();
        }

        Map<String, String> course = new LinkedHashMap<>();
        course.put("section", "금학기수강학점");
        course.put("courseCode", matcher.group(2));
        course.put("courseName", matcher.group(3).trim());
        course.put("credits", matcher.group(4));
        String rawClassification = findRawClassificationText(line.segments(), CourseSide.LEFT);
        if (rawClassification != null) {
            course.put("rawClassification", rawClassification);
        }
        return Optional.of(course);
    }

    private String leftSectionName(String text) {
        if (text.startsWith("필수교과")) {
            return "필수교과";
        }
        if (text.startsWith("배분이수")) {
            return "배분이수";
        }
        if (text.startsWith("자유이수")) {
            return "자유이수";
        }
        if (text.startsWith("기타")) {
            return "기타";
        }
        return null;
    }

    private TextSegment findFirstMatchingSegment(List<TextSegment> segments, Pattern pattern) {
        return segments.stream()
                .filter(segment -> pattern.matcher(segment.text()).find())
                .findFirst()
                .orElse(null);
    }

    private String firstMatch(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group() : text;
    }

    private TextSegment findSemesterSegment(List<TextSegment> segments, CourseSide side) {
        return segments.stream()
                .filter(segment -> SEMESTER_PATTERN.matcher(segment.text()).find())
                .filter(segment -> side == CourseSide.LEFT ? segment.x1() >= 145 && segment.x1() < 235
                        : segment.x1() >= 390 && segment.x1() < 570)
                .findFirst()
                .orElse(null);
    }

    private TextSegment findBrokenSemesterSlashSegment(List<TextSegment> segments, CourseSide side) {
        return segments.stream()
                .filter(segment -> segment.text().contains("/"))
                .filter(segment -> side == CourseSide.LEFT ? segment.x1() >= 180 && segment.x1() < 235
                        : segment.x1() >= 390 && segment.x1() < 570)
                .findFirst()
                .orElse(null);
    }

    private TextSegment findBrokenSemesterNumberSegment(
            List<TextSegment> segments,
            TextSegment slashSegment,
            CourseSide side
    ) {
        return segments.stream()
                .filter(segment -> segment.text().matches("\\d"))
                .filter(segment -> side == CourseSide.LEFT ? segment.x1() > slashSegment.x1() && segment.x1() < 235
                        : segment.x1() > slashSegment.x1() && segment.x1() < 570)
                .findFirst()
                .orElse(null);
    }

    private TextSegment findCreditSegment(List<TextSegment> segments, CourseSide side) {
        return segments.stream()
                .filter(segment -> segment.text().matches("\\d"))
                .filter(segment -> side == CourseSide.LEFT ? segment.x1() >= 130 && segment.x1() < 190
                        : segment.x1() >= 330 && segment.x1() < 570)
                .findFirst()
                .orElse(null);
    }

    private TextSegment findRawClassificationSegment(List<TextSegment> segments, CourseSide side) {
        return segments.stream()
                .filter(segment -> segment.text().matches("\\d{2,4}"))
                .filter(segment -> side == CourseSide.LEFT ? segment.x1() < 80
                        : segment.x1() >= 190 && segment.x1() < 285)
                .findFirst()
                .orElse(null);
    }

    // 금학기수강학점 구역의 "08 11"처럼 이수구분 코드가 같은 줄에 두 세그먼트로 나뉘어 찍히는 경우, 첫 번째만
    // 가져오면 앞자리(예: "08")가 누락된다. x좌표 순으로 전부 이어붙여 "0811" 형태로 복원한다.
    private String findRawClassificationText(List<TextSegment> segments, CourseSide side) {
        String text = segments.stream()
                .filter(segment -> segment.text().matches("\\d{2,4}"))
                .filter(segment -> side == CourseSide.LEFT ? segment.x1() < 80
                        : segment.x1() >= 190 && segment.x1() < 285)
                .sorted(Comparator.comparingDouble(TextSegment::x1))
                .map(TextSegment::text)
                .collect(Collectors.joining());
        return text.isEmpty() ? null : text;
    }

    private String extractCourseName(
            List<TextSegment> segments,
            TextSegment codeSegment,
            TextSegment creditSegment,
            TextSegment semesterSegment
    ) {
        double endX = creditSegment == null ? semesterSegment.x1() : creditSegment.x1();
        return segments.stream()
                .filter(segment -> segment.x1() > codeSegment.x2() && segment.x2() <= endX + 1)
                .map(TextSegment::text)
                .reduce((left, right) -> left + " " + right)
                .orElse("")
                .trim();
    }

    private String extractCourseNameUntilSlash(
            List<TextSegment> segments,
            TextSegment codeSegment,
            TextSegment slashSegment
    ) {
        return segments.stream()
                .filter(segment -> segment.x1() > codeSegment.x2() && segment.x1() < slashSegment.x1())
                .map(TextSegment::text)
                .reduce((left, right) -> left + " " + right)
                .orElse("")
                .trim();
    }

    private String lastDigits(String text, int count) {
        StringBuilder digits = new StringBuilder();
        for (int index = text.length() - 1; index >= 0; index--) {
            char character = text.charAt(index);
            if (Character.isDigit(character)) {
                digits.append(character);
                if (digits.length() == count) {
                    return digits.reverse().toString();
                }
            }
        }
        return null;
    }

    private String removeLastDigits(String text, int count) {
        StringBuilder reversed = new StringBuilder();
        int removed = 0;
        for (int index = text.length() - 1; index >= 0; index--) {
            char character = text.charAt(index);
            if (removed < count && Character.isDigit(character)) {
                removed++;
                continue;
            }
            reversed.append(character);
        }
        return reversed.reverse().toString();
    }

    private String cleanRecoveredCourseName(String courseName) {
        return courseName
                .replace("�", "")
                .replace("∬", "")
                .replaceFirst("^취\\s*", "")
                .trim();
    }

    private String trailingCredit(String courseName) {
        if (courseName.isBlank()) {
            return null;
        }
        String lastCharacter = courseName.substring(courseName.length() - 1);
        return lastCharacter.matches("\\d") ? lastCharacter : null;
    }

    private EmbeddedCredit embeddedCredit(String courseName) {
        Matcher seminarMatcher = Pattern.compile("세([1-9])미나").matcher(courseName);
        if (seminarMatcher.find()) {
            String credits = seminarMatcher.group(1);
            return new EmbeddedCredit(
                    seminarMatcher.replaceFirst("세미나").trim(),
                    credits
            );
        }
        Matcher koreanCreditMatcher = EMBEDDED_KOREAN_CREDIT_PATTERN.matcher(courseName);
        if (koreanCreditMatcher.find()) {
            String credits = koreanCreditMatcher.group(1);
            return new EmbeddedCredit(
                    koreanCreditMatcher.replaceFirst("").trim(),
                    credits
            );
        }
        return null;
    }

    private List<TextLine> flattenTextLines(List<PageText> pageTexts) {
        return pageTexts.stream()
                .flatMap(pageText -> pageText.lines().stream())
                .toList();
    }

    private enum CourseSide {
        LEFT,
        RIGHT;

        boolean contains(TextSegment segment, TextLine line) {
            double boundary = columnBoundary(line);
            return this == LEFT ? segment.x1() < boundary : segment.x1() >= boundary;
        }

        private double columnBoundary(TextLine line) {
            return line.segments().stream()
                    .filter(segment -> MAJOR_CODE_PATTERN.matcher(segment.text()).find()
                            || MAJOR_HEADER_SEGMENT_PATTERN.matcher(segment.text()).matches())
                    .mapToDouble(TextSegment::x1)
                    .filter(x -> x >= 170)
                    .min()
                    .orElse(235.0);
        }
    }

    private static class CourseSectionState {

        private String leftSection = "";
        private String rightSection;
        private String leftRawClassification;
        private String rightRawClassification;

        CourseSectionState(String rightSection) {
            this.rightSection = rightSection == null ? "" : rightSection;
        }

        String leftSection() {
            return leftSection;
        }

        void leftSection(String leftSection) {
            this.leftSection = leftSection;
        }

        String rightSection() {
            return rightSection;
        }

        void rightSection(String rightSection) {
            this.rightSection = rightSection;
        }

        String rawClassification(CourseSide side) {
            return side == CourseSide.LEFT ? leftRawClassification : rightRawClassification;
        }

        void rawClassification(CourseSide side, String rawClassification) {
            if (side == CourseSide.LEFT) {
                this.leftRawClassification = rawClassification;
            } else {
                this.rightRawClassification = rawClassification;
            }
        }
    }

    private String findLineStartingWith(List<String> lines, String prefix) {
        return lines.stream()
                .filter(line -> line.startsWith(prefix))
                .findFirst()
                .orElse("");
    }

    private int indexOf(List<String> lines, String keyword) {
        for (int index = 0; index < lines.size(); index++) {
            if (lines.get(index).contains(keyword)) {
                return index;
            }
        }
        return -1;
    }

    private static class PositionCollectingTextStripper extends PDFTextStripper {

        private final List<PageText> pages = new ArrayList<>();
        private int currentPageNumber;
        private List<TextPosition> currentPositions = new ArrayList<>();

        PositionCollectingTextStripper() throws IOException {
            super();
        }

        @Override
        protected void startPage(PDPage page) throws IOException {
            currentPageNumber++;
            currentPositions = new ArrayList<>();
            super.startPage(page);
        }

        @Override
        protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
            currentPositions.addAll(textPositions);
            super.writeString(text, textPositions);
        }

        @Override
        protected void endPage(PDPage page) throws IOException {
            pages.add(new PageText(currentPageNumber, groupLines(currentPositions)));
            super.endPage(page);
        }

        List<PageText> toPageTexts() {
            return pages;
        }

        private List<TextLine> groupLines(List<TextPosition> positions) {
            List<TextPosition> sortedPositions = positions.stream()
                    .sorted(Comparator.comparing(TextPosition::getYDirAdj)
                            .thenComparing(TextPosition::getXDirAdj))
                    .toList();

            List<List<TextPosition>> lineGroups = new ArrayList<>();
            for (TextPosition position : sortedPositions) {
                List<TextPosition> lineGroup = findLineGroup(lineGroups, position);
                if (lineGroup == null) {
                    lineGroup = new ArrayList<>();
                    lineGroups.add(lineGroup);
                }
                lineGroup.add(position);
            }

            return lineGroups.stream()
                    .map(this::toTextLine)
                    .filter(line -> !line.text().isBlank())
                    .toList();
        }

        private List<TextPosition> findLineGroup(List<List<TextPosition>> lineGroups, TextPosition position) {
            for (List<TextPosition> lineGroup : lineGroups) {
                TextPosition first = lineGroup.getFirst();
                if (Math.abs(first.getYDirAdj() - position.getYDirAdj()) <= SAME_LINE_Y_TOLERANCE) {
                    return lineGroup;
                }
            }
            return null;
        }

        private TextLine toTextLine(List<TextPosition> lineGroup) {
            List<TextPosition> sortedLine = lineGroup.stream()
                    .sorted(Comparator.comparing(TextPosition::getXDirAdj))
                    .toList();
            List<TextSegment> segments = new ArrayList<>();
            List<TextPosition> segmentPositions = new ArrayList<>();

            TextPosition previous = null;
            for (TextPosition position : sortedLine) {
                if (previous != null && startsNewSegment(previous, position)) {
                    segments.add(toTextSegment(segmentPositions));
                    segmentPositions = new ArrayList<>();
                }
                segmentPositions.add(position);
                previous = position;
            }
            if (!segmentPositions.isEmpty()) {
                segments.add(toTextSegment(segmentPositions));
            }

            String lineText = String.join(" ", segments.stream()
                    .map(TextSegment::text)
                    .filter(text -> !text.isBlank())
                    .toList());
            double y = sortedLine.stream().mapToDouble(TextPosition::getYDirAdj).average().orElse(0.0);
            return new TextLine(y, lineText, segments);
        }

        private boolean startsNewSegment(TextPosition previous, TextPosition current) {
            float previousEndX = previous.getXDirAdj() + previous.getWidthDirAdj();
            float gap = current.getXDirAdj() - previousEndX;
            float threshold = Math.max(previous.getWidthOfSpace(), previous.getHeightDir() * SEGMENT_GAP_RATIO);
            return gap > threshold;
        }

        private TextSegment toTextSegment(List<TextPosition> positions) {
            StringBuilder content = new StringBuilder();
            double minX = Double.MAX_VALUE;
            double maxX = Double.MIN_VALUE;

            for (TextPosition position : positions) {
                content.append(position.getUnicode());
                minX = Math.min(minX, position.getXDirAdj());
                maxX = Math.max(maxX, position.getXDirAdj() + position.getWidthDirAdj());
            }
            return new TextSegment(minX, maxX, content.toString().trim());
        }
    }

    private record PageText(int pageNumber, List<TextLine> lines) {
    }

    private record TextLine(double y, String text, List<TextSegment> segments) {
    }

    private record TextSegment(double x1, double x2, String text) {
    }

    private record EmbeddedCredit(String courseName, String credits) {
    }
}
