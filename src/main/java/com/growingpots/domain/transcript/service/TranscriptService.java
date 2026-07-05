package com.growingpots.domain.transcript.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.growingpots.domain.transcript.entity.enums.CertJudgement;
import com.growingpots.domain.transcript.entity.CertResult;
import com.growingpots.domain.transcript.entity.enums.CertType;
import com.growingpots.domain.transcript.entity.enums.CourseStatus;
import com.growingpots.domain.transcript.entity.GraduationAnalysisSummary;
import com.growingpots.domain.transcript.entity.enums.MajorType;
import com.growingpots.domain.transcript.entity.enums.RecordSource;
import com.growingpots.domain.transcript.entity.StudentCourse;
import com.growingpots.domain.transcript.entity.StudentMajor;
import com.growingpots.domain.transcript.parser.ParsedTranscript;
import com.growingpots.domain.transcript.parser.PdfParsingException;
import com.growingpots.domain.transcript.parser.PdfTranscriptParser;
import com.growingpots.domain.transcript.repository.CertResultRepository;
import com.growingpots.domain.transcript.repository.GraduationAnalysisSummaryRepository;
import com.growingpots.domain.transcript.repository.StudentCourseRepository;
import com.growingpots.domain.transcript.repository.StudentMajorRepository;
import com.growingpots.domain.university.entity.Department;
import com.growingpots.domain.university.repository.DepartmentRepository;
import com.growingpots.global.exception.BaseException;
import com.growingpots.global.response.error.ErrorCode;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class TranscriptService {

    private static final byte[] PDF_MAGIC_BYTES = {'%', 'P', 'D', 'F'};
    private static final String CURRENT_SEMESTER_SECTION = "금학기수강학점";
    private static final String RETAKE_SECTION = "재수강";
    private static final Pattern DIGITS_PATTERN = Pattern.compile("\\d+");
    private static final Pattern DECIMAL_PATTERN = Pattern.compile("\\d+(\\.\\d+)?");

    private final StudentCourseRepository studentCourseRepository;
    private final StudentMajorRepository studentMajorRepository;
    private final DepartmentRepository departmentRepository;
    private final GraduationAnalysisSummaryRepository graduationAnalysisSummaryRepository;
    private final CertResultRepository certResultRepository;
    private final PdfTranscriptParser pdfTranscriptParser;
    private final ObjectMapper objectMapper;

    @Transactional
    public void uploadTranscript(Long memberId, MultipartFile file) {
        byte[] pdfBytes = readBytes(file);
        validatePdfFormat(pdfBytes);

        ParsedTranscript parsed;
        try {
            parsed = pdfTranscriptParser.parse(pdfBytes);
        } catch (PdfParsingException e) {
            throw new BaseException(ErrorCode.PDF_PARSING_FAILED, e.getMessage());
        }
        logForVerification(parsed);

        studentCourseRepository.deleteByMemberIdAndSource(memberId, RecordSource.PDF);
        studentCourseRepository.saveAll(toStudentCourses(memberId, parsed.courses()));

        List<StudentMajor> studentMajors = saveMajorsAndSummaries(memberId, parsed);

        certResultRepository.deleteByMemberIdAndSource(memberId, RecordSource.PDF);
        certResultRepository.saveAll(toCertResults(memberId, studentMajors, parsed.graduationSummary()));
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new BaseException(ErrorCode.PDF_PARSING_FAILED, e.getMessage());
        }
    }

    private void validatePdfFormat(byte[] pdfBytes) {
        if (pdfBytes.length < PDF_MAGIC_BYTES.length) {
            throw new BaseException(ErrorCode.PDF_INVALID_FORMAT);
        }
        for (int i = 0; i < PDF_MAGIC_BYTES.length; i++) {
            if (pdfBytes[i] != PDF_MAGIC_BYTES[i]) {
                throw new BaseException(ErrorCode.PDF_INVALID_FORMAT);
            }
        }
    }

    // 자동 파싱 결과를 눈으로 검증하기 위한 용도 (DB에는 courses만 저장)
    private void logForVerification(ParsedTranscript parsed) {
        try {
            log.debug("졸업사정표 파싱 결과: {}", objectMapper.writeValueAsString(parsed));
        } catch (JsonProcessingException e) {
            log.warn("파싱 결과 로깅 실패: {}", e.getMessage());
        }
    }

    private List<StudentCourse> toStudentCourses(Long memberId, List<Map<String, String>> courses) {
        return courses.stream()
                .map(course -> toStudentCourse(memberId, course))
                .toList();
    }

    private StudentCourse toStudentCourse(Long memberId, Map<String, String> course) {
        String section = course.get("section");
        String rawClassification = course.get("rawClassification");
        String semester = course.get("semester");
        boolean inProgress = CURRENT_SEMESTER_SECTION.equals(section);

        return StudentCourse.builder()
                .memberId(memberId)
                .rawCourseCode(course.get("courseCode"))
                .rawCourseName(course.get("courseName"))
                .credit(Integer.parseInt(course.get("credits")))
                .takenYear(semester == null ? null : takenYear(semester))
                .takenSemester(semester == null ? null : takenSemester(semester))
                .section(section)
                .rawClassification(rawClassification)
                .isRetake(RETAKE_SECTION.equals(section))
                .status(inProgress ? CourseStatus.IN_PROGRESS : CourseStatus.COMPLETED)
                .source(RecordSource.PDF)
                .build();
    }

    // 파서가 내려주는 "yyyy/n" 형식(예: "2023/2")을 분리한다.
    private Integer takenYear(String semester) {
        return Integer.parseInt(semester.substring(0, semester.indexOf('/')));
    }

    private String takenSemester(String semester) {
        return semester.substring(semester.indexOf('/') + 1);
    }

    // 전공(본전공/복수전공)별로 STUDENT_MAJOR를 찾거나 만들고, GRADUATION_ANALYSIS_SUMMARY는 덮어쓴다.
    private List<StudentMajor> saveMajorsAndSummaries(Long memberId, ParsedTranscript parsed) {
        List<StudentMajor> studentMajors = new ArrayList<>();
        for (Map<String, String> majorRequirement : parsed.majorRequirements()) {
            StudentMajor studentMajor = findOrCreateStudentMajor(memberId, majorRequirement);
            studentMajors.add(studentMajor);

            GraduationAnalysisSummary newSummary = toGraduationAnalysisSummary(
                    studentMajor, majorRequirement, parsed.graduationSummary(), parsed.generalEducation());
            graduationAnalysisSummaryRepository.findByStudentMajor(studentMajor)
                    .ifPresentOrElse(
                            existing -> existing.updateFrom(newSummary),
                            () -> graduationAnalysisSummaryRepository.save(newSummary));
        }
        return studentMajors;
    }

    private StudentMajor findOrCreateStudentMajor(Long memberId, Map<String, String> majorRequirement) {
        MajorType majorType = toMajorType(majorRequirement.get("majorType"));
        Department department = findMatchingDepartment(majorRequirement.get("majorName"));

        if (department != null) {
            return studentMajorRepository.findByMemberIdAndDepartment(memberId, department)
                    .orElseGet(() -> studentMajorRepository.save(StudentMajor.builder()
                            .memberId(memberId)
                            .majorType(majorType)
                            .department(department)
                            .build()));
        }

        log.warn("전공명과 매칭되는 학과를 찾지 못함: {}", majorRequirement.get("majorName"));
        return studentMajorRepository.findByMemberIdAndDepartmentIsNullAndMajorType(memberId, majorType)
                .orElseGet(() -> studentMajorRepository.save(StudentMajor.builder()
                        .memberId(memberId)
                        .majorType(majorType)
                        .department(null)
                        .build()));
    }

    // PDF의 전공명("스포츠의학")과 DEPARTMENT.name("스포츠의학과")은 "학과/과" 접미사 유무가 달라 정규화 후 비교한다.
    private Department findMatchingDepartment(String majorName) {
        if (majorName == null) {
            return null;
        }
        String normalizedMajorName = normalizeDepartmentName(majorName);
        return departmentRepository.findAll().stream()
                .filter(department -> normalizeDepartmentName(department.getName()).equals(normalizedMajorName))
                .findFirst()
                .orElse(null);
    }

    // "스포츠의학과" = "스포츠의학"(전공명) + "과" 이므로 "학과"를 통째로 지우면 "학"까지 날아간다. "과"/"학부"만 벗겨낸다.
    private String normalizeDepartmentName(String name) {
        return name.replaceAll("(학부|과)$", "");
    }

    // ERD의 MAJOR_TYPE은 MAIN/DOUBLE 2종뿐이라, PDF의 4가지 표기(단일전공/심화전공/복수전공/다전공)를 2종으로 합친다.
    private MajorType toMajorType(String rawMajorType) {
        return switch (rawMajorType) {
            case "복수전공", "다전공" -> MajorType.DOUBLE;
            default -> MajorType.MAIN;
        };
    }

    private GraduationAnalysisSummary toGraduationAnalysisSummary(
            StudentMajor studentMajor,
            Map<String, String> majorRequirement,
            Map<String, String> graduationSummary,
            List<Map<String, String>> generalEducation
    ) {
        return GraduationAnalysisSummary.builder()
                .studentMajor(studentMajor)
                .totalCreditCurrent(extractInt(graduationSummary.get("earnedCredits")))
                .totalCreditRequired(extractInt(graduationSummary.get("requiredCredits")))
                .gpaCurrent(extractDecimal(graduationSummary.get("gpaEarned")))
                .gpaRequired(extractDecimal(graduationSummary.get("gpaRequirement")))
                .englishCurrent(extractInt(graduationSummary.get("englishLectureEarned")))
                .englishRequired(extractInt(graduationSummary.get("englishLectureRequirement")))
                .swCertCurrent(null)
                .swCertRequired(null)
                .majorBasicCurrent(extractInt(majorRequirement.get("basicEarned")))
                .majorBasicRequired(extractInt(majorRequirement.get("basicRequired")))
                .majorRequiredCurrent(extractInt(majorRequirement.get("requiredEarned")))
                .majorRequiredRequired(extractInt(majorRequirement.get("requiredRequired")))
                .majorElectiveCurrent(extractInt(majorRequirement.get("electiveEarned")))
                .majorElectiveRequired(extractInt(majorRequirement.get("electiveRequired")))
                .requiredPlusElectiveCurrent(extractInt(majorRequirement.get("requiredPlusElectiveEarned")))
                .requiredPlusElectiveRequired(extractInt(majorRequirement.get("requiredPlusElectiveRequired")))
                .requiredGeCurrent(generalEducationCredit(generalEducation, "필수교과", true))
                .requiredGeRequired(generalEducationCredit(generalEducation, "필수교과", false))
                .distributedGeCurrent(generalEducationCredit(generalEducation, "배분이수", true))
                .distributedGeRequired(generalEducationCredit(generalEducation, "배분이수", false))
                .freeGeCurrent(generalEducationCredit(generalEducation, "자유이수", true))
                .freeGeRequired(generalEducationCredit(generalEducation, "자유이수", false))
                .build();
    }

    // "배분이수교과(2024~)"처럼 연도 접미사가 붙으므로 접두 일치로 찾는다.
    private int generalEducationCredit(List<Map<String, String>> generalEducation, String categoryPrefix, boolean earned) {
        return generalEducation.stream()
                .filter(row -> row.get("category") != null && row.get("category").startsWith(categoryPrefix))
                .findFirst()
                .map(row -> extractInt(row.get(earned ? "creditsEarned" : "creditsRequired")))
                .orElse(0);
    }

    private List<CertResult> toCertResults(Long memberId, List<StudentMajor> studentMajors, Map<String, String> graduationSummary) {
        List<CertResult> certResults = new ArrayList<>();
        for (StudentMajor studentMajor : studentMajors) {
            certResults.add(toCertResult(memberId, studentMajor, CertType.THESIS, graduationSummary.get("thesisJudgement")));
            certResults.add(toCertResult(memberId, studentMajor, CertType.ENGLISH, graduationSummary.get("englishLectureJudgement")));
            certResults.add(toCertResult(memberId, studentMajor, CertType.SW, graduationSummary.get("swCertification")));
            certResults.add(toCertResult(memberId, studentMajor, CertType.TOPIK, graduationSummary.get("topik")));
            certResults.add(toCertResult(memberId, studentMajor, CertType.GRADUATION_CERT, graduationSummary.get("graduationCertification")));
        }
        return certResults;
    }

    private CertResult toCertResult(Long memberId, StudentMajor studentMajor, CertType certType, String rawJudgement) {
        return CertResult.builder()
                .memberId(memberId)
                .studentMajor(studentMajor)
                .certType(certType)
                .result(toCertJudgement(rawJudgement))
                .source(RecordSource.PDF)
                .build();
    }

    private CertJudgement toCertJudgement(String rawJudgement) {
        if (rawJudgement == null) {
            return CertJudgement.NONE;
        }
        return switch (rawJudgement) {
            case "통과" -> CertJudgement.PASS;
            case "미통과" -> CertJudgement.FAIL;
            case "해당없음" -> CertJudgement.NONE;
            case "면제" -> CertJudgement.EXEMPT;
            default -> {
                log.warn("알 수 없는 인증 판정값: {}", rawJudgement);
                yield CertJudgement.NONE;
            }
        };
    }

    // PDF 자체의 글자 깨짐(예: "픕2.788")이나 부가 표기("44(62)")를 방어적으로 걸러내고 숫자만 취한다.
    private int extractInt(String value) {
        if (value == null) {
            return 0;
        }
        Matcher matcher = DIGITS_PATTERN.matcher(value);
        return matcher.find() ? Integer.parseInt(matcher.group()) : 0;
    }

    private BigDecimal extractDecimal(String value) {
        if (value == null) {
            return null;
        }
        Matcher matcher = DECIMAL_PATTERN.matcher(value);
        return matcher.find() ? new BigDecimal(matcher.group()) : null;
    }
}
