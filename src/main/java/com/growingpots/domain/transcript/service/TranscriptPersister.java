package com.growingpots.domain.transcript.service;

import com.growingpots.domain.transcript.entity.enums.CertJudgement;
import com.growingpots.domain.transcript.entity.CertResult;
import com.growingpots.domain.transcript.entity.enums.CertType;
import com.growingpots.domain.transcript.entity.enums.CourseStatus;
import com.growingpots.domain.transcript.entity.GraduationAnalysisSummary;
import com.growingpots.domain.transcript.entity.enums.RecordSource;
import com.growingpots.domain.transcript.entity.enums.Semester;
import com.growingpots.domain.transcript.entity.StudentCourse;
import com.growingpots.domain.transcript.parser.ParsedTranscript;
import com.growingpots.domain.transcript.repository.CertResultRepository;
import com.growingpots.domain.transcript.repository.GraduationAnalysisSummaryRepository;
import com.growingpots.domain.transcript.repository.StudentCourseRepository;
import com.growingpots.domain.university.entity.Course;
import com.growingpots.domain.university.entity.Department;
import com.growingpots.domain.university.entity.Division;
import com.growingpots.domain.university.entity.enums.DivisionCategory;
import com.growingpots.domain.university.repository.CourseRepository;
import com.growingpots.domain.university.repository.DepartmentRepository;
import com.growingpots.domain.university.repository.DivisionRepository;
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
import java.util.stream.Collectors;
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

    // 교양 4개 표(section 라벨) → Division.category. 코드("02"처럼 배분이수/자유이수가 같은 코드를 쓰는 경우가 있어)
    // 대신 표 이름으로 직접 매핑한다.
    private static final Map<String, DivisionCategory> GE_SECTION_CATEGORIES = Map.of(
            "필수교과", DivisionCategory.GE_REQUIRED,
            "배분이수", DivisionCategory.GE_DISTRIBUTION,
            "자유이수", DivisionCategory.GE_FREE,
            "기타", DivisionCategory.GENERAL_ELECTIVE
    );

    private final StudentProfileRepository studentProfileRepository;
    private final StudentCourseRepository studentCourseRepository;
    private final StudentMajorRepository studentMajorRepository;
    private final DepartmentRepository departmentRepository;
    private final CourseRepository courseRepository;
    private final DivisionRepository divisionRepository;
    private final GraduationAnalysisSummaryRepository graduationAnalysisSummaryRepository;
    private final CertResultRepository certResultRepository;

    @Transactional
    public void persist(StudentProfile studentProfile, ParsedTranscript parsed) {
        // 진행 중 과목의 수강년도/학기 계산에 쓰는 "오늘"을 한 번만 고정해, 학기 경계를 걸치는 순간에도
        // 같은 persist() 호출 안에서는 일관된 값을 쓰도록 한다.
        LocalDate now = LocalDate.now();
        updateStudentProfile(studentProfile, parsed.studentInfo(), now);

        // 과목 수만큼 학수번호로 매번 조회하지 않도록 COURSE 마스터를 한 번만 불러와 재사용한다.
        // 학교별로 학수번호가 겹칠 수 있어 학생 소속 학교로 한정한다.
        Map<String, Course> coursesByCode = courseRepository.findBySchool(studentProfile.getSchool()).stream()
                .collect(Collectors.toMap(Course::getCourseCode, c -> c, (a, b) -> a));

        List<Division> divisions = divisionRepository.findBySchool(studentProfile.getSchool());
        Map<DivisionCategory, Division> divisionsByCategory = divisions.stream()
                .collect(Collectors.toMap(Division::getCategory, d -> d, (a, b) -> a));
        Map<String, Division> divisionsByCode = divisions.stream()
                .collect(Collectors.toMap(Division::getCode, d -> d, (a, b) -> a));

        studentCourseRepository.deleteByStudentProfileAndSource(studentProfile, RecordSource.PDF);
        studentCourseRepository.saveAll(toStudentCourses(
                studentProfile, parsed.courses(), coursesByCode, divisionsByCategory, divisionsByCode, now));

        // 전공이 여러 개(복수전공)여도 학과 목록은 한 번만 조회해서 재사용한다.
        List<Department> departments = departmentRepository.findAll();
        List<StudentMajor> studentMajors = saveMajorsAndSummaries(studentProfile, parsed, departments);

        certResultRepository.deleteByStudentProfileAndSource(studentProfile, RecordSource.PDF);
        certResultRepository.saveAll(toCertResults(studentProfile, studentMajors, parsed.graduationSummary()));
    }

    private void updateStudentProfile(StudentProfile studentProfile, Map<String, String> studentInfo, LocalDate now) {
        String studentNo = studentInfo.get("studentId");
        studentProfile.updateAcademicInfo(
                studentNo,
                studentInfo.get("academicStatus"),
                extractGrade(studentInfo.get("grade")),
                extractAdmissionYear(studentNo),
                computeCurrentTerm(now));
        studentProfileRepository.save(studentProfile);
    }

    // 연도는 학번의 앞 4자리를 가져옴.
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
    private int computeCurrentTerm(LocalDate now) {
        int month = now.getMonthValue();
        return (month >= 3 && month <= 8) ? 1 : 2;
    }

    // 학사년도 기준 계산. 1~2월은 달력상 다음 해지만 학사년도로는 전년도 2학기이므로 연도에서 1을 뺀다.
    private int computeCurrentAcademicYear(LocalDate now) {
        return now.getMonthValue() <= 2 ? now.getYear() - 1 : now.getYear();
    }

    private Integer extractGrade(String value) {
        if (value == null) {
            return null;
        }
        Matcher matcher = DIGITS_PATTERN.matcher(value);
        return matcher.find() ? Integer.valueOf(matcher.group()) : null;
    }

    private List<StudentCourse> toStudentCourses(
            StudentProfile studentProfile, List<Map<String, String>> courses, Map<String, Course> coursesByCode,
            Map<DivisionCategory, Division> divisionsByCategory, Map<String, Division> divisionsByCode,
            LocalDate now) {
        return courses.stream()
                .map(course -> toStudentCourse(studentProfile, course, coursesByCode, divisionsByCategory, divisionsByCode, now))
                .toList();
    }

    private StudentCourse toStudentCourse(
            StudentProfile studentProfile, Map<String, String> course, Map<String, Course> coursesByCode,
            Map<DivisionCategory, Division> divisionsByCategory, Map<String, Division> divisionsByCode,
            LocalDate now) {
        String section = course.get("section");
        String rawClassification = course.get("rawClassification");
        String semester = course.get("semester");
        String rawCourseCode = course.get("courseCode");
        boolean inProgress = CURRENT_SEMESTER_SECTION.equals(section);

        // 금학기수강학점(진행 중) 과목은 PDF에 수강년도/학기가 안 찍혀 있어 오늘 날짜 기준으로 채운다.
        Integer takenYear = semester != null ? takenYear(semester) : (inProgress ? computeCurrentAcademicYear(now) : null);
        Semester takenSemester = semester != null ? takenSemester(semester)
                : (inProgress ? toSemester(computeCurrentTerm(now)) : null);

        return StudentCourse.builder()
                .studentProfile(studentProfile)
                .course(rawCourseCode == null ? null : coursesByCode.get(rawCourseCode))
                .appliedDivision(resolveAppliedDivision(section, rawClassification, divisionsByCategory, divisionsByCode))
                .rawCourseCode(rawCourseCode)
                .rawCourseName(course.get("courseName"))
                .credit(Integer.parseInt(course.get("credits")))
                .takenYear(takenYear)
                .takenSemester(takenSemester)
                .rawClassification(rawClassification)
                .isRetake(RETAKE_SECTION.equals(section))
                .status(inProgress ? CourseStatus.IN_PROGRESS : CourseStatus.COMPLETED)
                .source(RecordSource.PDF)
                .build();
    }

    // 교양 4개 표는 section 라벨로 category를 바로 찾고, "08"이 포함된 코드(금학기수강학점 기타 과목 등)는
    // 일반선택으로, 그 외엔 전공 코드(04/05/11)로 정확히 일치하는 Division을 찾는다. 매칭 안 되면 null.
    private Division resolveAppliedDivision(
            String section, String rawClassification,
            Map<DivisionCategory, Division> divisionsByCategory, Map<String, Division> divisionsByCode) {
        DivisionCategory geCategory = GE_SECTION_CATEGORIES.get(section);
        if (geCategory != null) {
            return divisionsByCategory.get(geCategory);
        }
        if (rawClassification != null && rawClassification.contains("08")) {
            return divisionsByCode.get("08");
        }
        return rawClassification == null ? null : divisionsByCode.get(rawClassification);
    }

    // 파서가 내려주는 "yyyy/n" 형식(예: "2023/2")을 분리한다.
    private Integer takenYear(String semester) {
        return Integer.parseInt(semester.substring(0, semester.indexOf('/')));
    }

    // PDF는 계절학기 구분 없이 "1"/"2"만 내려준다. SUMMER/WINTER는 사용자가 직접 편집할 때만 쓰인다.
    private Semester takenSemester(String semester) {
        return toSemester(Integer.parseInt(semester.substring(semester.indexOf('/') + 1)));
    }

    private Semester toSemester(int term) {
        return term == 1 ? Semester.FIRST : Semester.SECOND;
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

    private String normalizeDepartmentName(String name) {
        return name.replaceAll("(부|과)$", "");
    }

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
                .generalElectiveCurrent(extractInt(graduationSummary.get("generalElectiveEarned")))
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
