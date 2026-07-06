package com.growingpots.domain.transcript.service;

import com.growingpots.domain.transcript.entity.enums.CertJudgement;
import com.growingpots.domain.transcript.entity.CertResult;
import com.growingpots.domain.transcript.entity.enums.CertType;
import com.growingpots.domain.transcript.entity.enums.CourseStatus;
import com.growingpots.domain.transcript.entity.GraduationAnalysisSummary;
import com.growingpots.domain.transcript.entity.enums.RecordSource;
import com.growingpots.domain.transcript.entity.StudentCourse;
import com.growingpots.domain.transcript.parser.ParsedTranscript;
import com.growingpots.domain.transcript.repository.CertResultRepository;
import com.growingpots.domain.transcript.repository.GraduationAnalysisSummaryRepository;
import com.growingpots.domain.transcript.repository.StudentCourseRepository;
import com.growingpots.domain.university.entity.Department;
import com.growingpots.domain.university.repository.DepartmentRepository;
import com.growingpots.domain.user.entity.StudentMajor;
import com.growingpots.domain.user.entity.StudentMajor.MajorType;
import com.growingpots.domain.user.entity.StudentProfile;
import com.growingpots.domain.user.repository.StudentMajorRepository;
import com.growingpots.domain.user.repository.StudentProfileRepository;
import com.growingpots.global.exception.BaseException;
import com.growingpots.global.response.error.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// PDF 파싱(TranscriptService)과 DB 저장을 분리한 클래스. 파싱은 DB 커넥션이 필요 없는 작업이라
// @Transactional 범위를 저장 로직만으로 좁히기 위해 별도 빈으로 뺐다(같은 클래스 내 호출은 AOP 프록시를 안 타서 분리 필수).
@Slf4j
@Component
@RequiredArgsConstructor
public class TranscriptPersister {

    private static final String CURRENT_SEMESTER_SECTION = "금학기수강학점";
    private static final String RETAKE_SECTION = "재수강";
    private static final Pattern DIGITS_PATTERN = Pattern.compile("\\d+");
    private static final Pattern DECIMAL_PATTERN = Pattern.compile("\\d+(\\.\\d+)?");

    private final StudentProfileRepository studentProfileRepository;
    private final StudentCourseRepository studentCourseRepository;
    private final StudentMajorRepository studentMajorRepository;
    private final DepartmentRepository departmentRepository;
    private final GraduationAnalysisSummaryRepository graduationAnalysisSummaryRepository;
    private final CertResultRepository certResultRepository;

    @Transactional
    public void persist(StudentProfile studentProfile, ParsedTranscript parsed) {
        updateStudentProfile(studentProfile, parsed.studentInfo());

        studentCourseRepository.deleteByStudentProfileAndSource(studentProfile, RecordSource.PDF);
        studentCourseRepository.saveAll(toStudentCourses(studentProfile, parsed.courses()));

        // 전공이 여러 개(복수전공)여도 학과 목록은 한 번만 조회해서 재사용한다.
        List<Department> departments = departmentRepository.findAll();
        List<StudentMajor> studentMajors = saveMajorsAndSummaries(studentProfile, parsed, departments);

        certResultRepository.deleteByStudentProfileAndSource(studentProfile, RecordSource.PDF);
        certResultRepository.saveAll(toCertResults(studentProfile, studentMajors, parsed.graduationSummary()));
    }

    private void updateStudentProfile(StudentProfile studentProfile, Map<String, String> studentInfo) {
        String studentNo = studentInfo.get("studentId");
        studentProfile.updateAcademicInfo(
                studentNo,
                studentInfo.get("academicStatus"),
                extractGrade(studentInfo.get("grade")),
                extractAdmissionYear(studentNo),
                computeCurrentTerm());
        studentProfileRepository.save(studentProfile);
    }

    // 국내 대학 학번은 앞 4자리가 입학연도인 관례를 따른다.
    private Integer extractAdmissionYear(String studentNo) {
        if (studentNo == null || studentNo.length() < 4) {
            return null;
        }
        try {
            return Integer.valueOf(studentNo.substring(0, 4));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // PDF에 현재 학기 정보가 없어 오늘 날짜로 계산한다: 1학기=3~8월, 2학기=9~2월(다음 해 2월까지 이어짐).
    private int computeCurrentTerm() {
        int month = LocalDate.now().getMonthValue();
        return (month >= 3 && month <= 8) ? 1 : 2;
    }

    private Integer extractGrade(String value) {
        if (value == null) {
            return null;
        }
        Matcher matcher = DIGITS_PATTERN.matcher(value);
        return matcher.find() ? Integer.valueOf(matcher.group()) : null;
    }

    private List<StudentCourse> toStudentCourses(StudentProfile studentProfile, List<Map<String, String>> courses) {
        return courses.stream()
                .map(course -> toStudentCourse(studentProfile, course))
                .toList();
    }

    private StudentCourse toStudentCourse(StudentProfile studentProfile, Map<String, String> course) {
        String section = course.get("section");
        String rawClassification = course.get("rawClassification");
        String semester = course.get("semester");
        boolean inProgress = CURRENT_SEMESTER_SECTION.equals(section);

        return StudentCourse.builder()
                .studentProfile(studentProfile)
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
    private List<StudentMajor> saveMajorsAndSummaries(
            StudentProfile studentProfile, ParsedTranscript parsed, List<Department> departments) {
        List<StudentMajor> studentMajors = new ArrayList<>();
        for (Map<String, String> majorRequirement : parsed.majorRequirements()) {
            StudentMajor studentMajor = findOrCreateStudentMajor(
                    studentProfile, majorRequirement, parsed.studentInfo(), departments);
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

    private StudentMajor findOrCreateStudentMajor(
            StudentProfile studentProfile,
            Map<String, String> majorRequirement,
            Map<String, String> studentInfo,
            List<Department> departments
    ) {
        MajorType majorType = toMajorType(majorRequirement.get("majorType"));
        String majorName = majorRequirement.get("majorName");
        Department matched = findMatchingDepartment(departments, majorName);

        // majorName이 학과명이 아니라 트랙명(예: "영화트랙")인 학과도 있다. 본전공은 학생정보의 학과명으로 한 번 더 시도한다.
        if (matched == null && majorType == MajorType.MAIN) {
            matched = findMatchingDepartment(departments, studentInfo.get("department"));
        }
        if (matched == null) {
            throw new BaseException(ErrorCode.MAJOR_NOT_FOUND, majorName);
        }

        Department department = matched;
        return studentMajorRepository.findByStudentProfileAndDepartment(studentProfile, department)
                .orElseGet(() -> studentMajorRepository.save(StudentMajor.builder()
                        .studentProfile(studentProfile)
                        .majorType(majorType)
                        .department(department)
                        .build()));
    }

    // PDF의 전공명("스포츠의학")과 DEPARTMENT.name("스포츠의학과")은 "학과/과" 접미사 유무가 달라 정규화 후 비교한다.
    private Department findMatchingDepartment(List<Department> departments, String majorName) {
        if (majorName == null) {
            return null;
        }
        String normalizedMajorName = normalizeDepartmentName(majorName);
        return departments.stream()
                .filter(department -> normalizeDepartmentName(department.getName()).equals(normalizedMajorName))
                .findFirst()
                .orElse(null);
    }

    // "스포츠의학과"="스포츠의학"+"과", "컴퓨터공학부"="컴퓨터공학"+"부" 이므로 "학"까지 포함해서 지우면 전공명이 잘린다. "과"/"부"만 벗겨낸다.
    private String normalizeDepartmentName(String name) {
        return name.replaceAll("(부|과)$", "");
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
                .swCertCurrent(extractInt(graduationSummary.get("swCertEarned")))
                .swCertRequired(extractInt(graduationSummary.get("swCertRequirement")))
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

    private List<CertResult> toCertResults(StudentProfile studentProfile, List<StudentMajor> studentMajors, Map<String, String> graduationSummary) {
        List<CertResult> certResults = new ArrayList<>();
        for (StudentMajor studentMajor : studentMajors) {
            certResults.add(toCertResult(studentProfile, studentMajor, CertType.THESIS, graduationSummary.get("thesisJudgement")));
            certResults.add(toCertResult(studentProfile, studentMajor, CertType.ENGLISH, graduationSummary.get("englishLectureJudgement")));
            certResults.add(toCertResult(studentProfile, studentMajor, CertType.SW, graduationSummary.get("swCertification")));
            certResults.add(toCertResult(studentProfile, studentMajor, CertType.TOPIK, graduationSummary.get("topik")));
            certResults.add(toCertResult(studentProfile, studentMajor, CertType.GRADUATION_CERT, graduationSummary.get("graduationCertification")));
        }
        return certResults;
    }

    private CertResult toCertResult(StudentProfile studentProfile, StudentMajor studentMajor, CertType certType, String rawJudgement) {
        return CertResult.builder()
                .studentProfile(studentProfile)
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
