package com.growingpots.domain.graduation.service;

import com.growingpots.domain.graduation.dto.response.GraduationCourseResponse;
import com.growingpots.domain.graduation.dto.response.GraduationCourseResponse.CourseInfo;
import com.growingpots.domain.graduation.dto.response.GraduationCourseResponse.MajorCourses;
import com.growingpots.domain.graduation.dto.response.GraduationResponse;
import com.growingpots.domain.graduation.dto.response.GraduationResponse.CertInfo;
import com.growingpots.domain.graduation.dto.response.GraduationResponse.ConditionInfo;
import com.growingpots.domain.graduation.dto.response.GraduationResponse.CreditInfo;
import com.growingpots.domain.graduation.dto.response.GraduationResponse.GpaInfo;
import com.growingpots.domain.graduation.dto.response.GraduationResponse.Summary;
import com.growingpots.domain.graduation.enums.GraduationConditionType;
import com.growingpots.domain.graduation.enums.MajorTypeFilter;
import com.growingpots.domain.transcript.entity.CertResult;
import com.growingpots.domain.transcript.entity.GraduationAnalysisSummary;
import com.growingpots.domain.transcript.entity.StudentCourse;
import com.growingpots.domain.transcript.entity.enums.Semester;
import com.growingpots.domain.transcript.repository.CertResultRepository;
import com.growingpots.domain.university.entity.enums.DivisionCategory;
import com.growingpots.domain.transcript.repository.GraduationAnalysisSummaryRepository;
import com.growingpots.domain.transcript.repository.StudentCourseRepository;
import com.growingpots.domain.university.entity.Division;
import com.growingpots.domain.university.entity.RequirementCourse;
import com.growingpots.domain.university.entity.RequirementCourseItem;
import com.growingpots.domain.university.entity.enums.OpenedSemester;
import com.growingpots.domain.university.repository.DivisionRepository;
import com.growingpots.domain.university.repository.RequirementCourseItemRepository;
import com.growingpots.domain.university.repository.RequirementCourseRepository;
import com.growingpots.domain.user.entity.StudentMajor;
import com.growingpots.domain.user.entity.StudentMajor.MajorType;
import com.growingpots.domain.user.entity.StudentProfile;
import com.growingpots.domain.user.repository.StudentMajorRepository;
import com.growingpots.domain.user.repository.StudentProfileRepository;
import com.growingpots.global.exception.BaseException;
import com.growingpots.global.response.error.ErrorCode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GraduationService {

    private final StudentProfileRepository studentProfileRepository;
    private final StudentMajorRepository studentMajorRepository;
    private final GraduationAnalysisSummaryRepository graduationAnalysisSummaryRepository;
    private final CertResultRepository certResultRepository;
    private final StudentCourseRepository studentCourseRepository;
    private final DivisionRepository divisionRepository;
    private final RequirementCourseRepository requirementCourseRepository;
    private final RequirementCourseItemRepository requirementCourseItemRepository;

    @Transactional(readOnly = true)
    public GraduationResponse getGraduation(Long memberId, MajorTypeFilter majorTypeFilter) {
        StudentProfile profile = studentProfileRepository.findWithDetailsByMemberId(memberId)
                .orElseThrow(() -> new BaseException(ErrorCode.STUDENT_PROFILE_NOT_FOUND));

        List<StudentMajor> majors = studentMajorRepository.findWithDepartmentByStudentProfile(profile);

        StudentMajor mainMajor = majors.stream()
                .filter(m -> m.getMajorType() == MajorType.MAIN)
                .findFirst()
                .orElseThrow(() -> new BaseException(ErrorCode.STUDENT_PROFILE_NOT_FOUND));

        // TODO(planner-source): source=PLANNED 요청 시 플래너 데이터 기반 계산 구현
        return switch (majorTypeFilter) {
            case PRIMARY -> {
                GraduationAnalysisSummary summary = requireSummary(mainMajor);
                List<CertResult> certs = certResultRepository.findByStudentMajor(mainMajor);
                yield buildResponse(profile, summary, null, certs);
            }
            case MULTI -> {
                StudentMajor doubleMajor = majors.stream()
                        .filter(m -> m.getMajorType() == MajorType.DOUBLE)
                        .findFirst()
                        .orElseThrow(() -> new BaseException(ErrorCode.DOUBLE_MAJOR_NOT_FOUND));
                GraduationAnalysisSummary summary = requireSummary(doubleMajor);
                List<CertResult> certs = certResultRepository.findByStudentMajor(doubleMajor);
                yield buildResponse(profile, summary, null, certs);
            }
            case ALL -> {
                GraduationAnalysisSummary mainSummary = requireSummary(mainMajor);
                GraduationAnalysisSummary doubleSummary = majors.stream()
                        .filter(m -> m.getMajorType() == MajorType.DOUBLE)
                        .findFirst()
                        .flatMap(graduationAnalysisSummaryRepository::findByStudentMajor)
                        .orElse(null);
                List<CertResult> certs = certResultRepository.findByStudentMajor(mainMajor);
                yield buildResponse(profile, mainSummary, doubleSummary, certs);
            }
        };
    }

    @Transactional(readOnly = true)
    public GraduationCourseResponse getCoursesByDivision(Long memberId, String divisionCodeStr, MajorTypeFilter majorTypeFilter) {
        GraduationConditionType conditionType = parseDivisionCode(divisionCodeStr);

        StudentProfile profile = studentProfileRepository.findWithDetailsByMemberId(memberId)
                .orElseThrow(() -> new BaseException(ErrorCode.STUDENT_PROFILE_NOT_FOUND));

        List<StudentMajor> majors = studentMajorRepository.findWithDepartmentByStudentProfile(profile);
        List<StudentMajor> targetMajors = filterMajors(majors, majorTypeFilter);

        if (majorTypeFilter == MajorTypeFilter.MULTI && targetMajors.isEmpty()) {
            throw new BaseException(ErrorCode.DOUBLE_MAJOR_NOT_FOUND);
        }

        List<MajorCourses> majorCoursesList = targetMajors.stream()
                .map(major -> buildMajorCourses(profile, major, conditionType))
                .toList();

        return GraduationCourseResponse.builder()
                .divisionCode(conditionType.name())
                .divisionName(conditionType.getDisplayName())
                .majors(majorCoursesList)
                .build();
    }

    private MajorCourses buildMajorCourses(StudentProfile profile, StudentMajor major, GraduationConditionType conditionType) {
        GraduationAnalysisSummary summary = requireSummary(major);

        int current = conditionType.getCurrentExtractor().applyAsInt(summary);
        Integer required = conditionType.getRequiredExtractor() != null
                ? conditionType.getRequiredExtractor().apply(summary) : null;
        boolean satisfied = required == null || current >= required;

        List<StudentCourse> takenCourses = fetchTakenCourses(profile, conditionType);

        int admissionYear = profile.getAdmissionYear();
        Optional<Division> divisionOpt = toDivisionCategory(conditionType)
                .flatMap(cat -> divisionRepository.findBySchoolAndCategory(profile.getSchool(), cat));

        List<RequirementCourse> requirementCourses = divisionOpt
                .map(div -> requirementCourseRepository.findApplicable(
                        major.getDepartment(), div, admissionYear, major.getTrack()))
                .orElse(List.of());
        boolean hasRequiredList = !requirementCourses.isEmpty();

        List<RequirementCourseItem> allItems = hasRequiredList
                ? requirementCourseItemRepository.findWithCourseByRequirementCourseIn(requirementCourses)
                : List.of();

        Map<Long, String> trackTypeMap = buildTrackTypeMap(allItems);

        Set<Long> takenCourseIds = new HashSet<>();
        List<CourseInfo> courses = new ArrayList<>();

        for (StudentCourse sc : takenCourses) {
            if (sc.getCourse() != null) {
                takenCourseIds.add(sc.getCourse().getId());
            }
            String trackType = sc.getCourse() != null
                    ? trackTypeMap.getOrDefault(sc.getCourse().getId(), "NONE") : "NONE";
            courses.add(toTakenCourseInfo(sc, trackType));
        }

        // 미이수 과목: RequirementCourseItem 중 이수하지 않은 것. courseId 기준 중복 제거.
        if (hasRequiredList) {
            Set<Long> addedIds = new HashSet<>();
            for (RequirementCourseItem item : allItems) {
                Long courseId = item.getCourse().getId();
                if (!takenCourseIds.contains(courseId) && addedIds.add(courseId)) {
                    String trackType = trackTypeMap.getOrDefault(courseId, "NONE");
                    courses.add(toNotTakenCourseInfo(item, trackType));
                }
            }
        }

        courses.sort(Comparator.comparing(CourseInfo::getName));

        return MajorCourses.builder()
                .majorType(major.getMajorType().name())
                .departmentName(major.getDepartment().getName())
                .current(current)
                .required(required)
                .satisfied(satisfied)
                .hasRequiredList(hasRequiredList)
                .courses(courses)
                .build();
    }

    private List<StudentCourse> fetchTakenCourses(StudentProfile profile, GraduationConditionType conditionType) {
        if (conditionType == GraduationConditionType.ENGLISH_COURSE) {
            return studentCourseRepository.findByStudentProfileAndCourseIsEnglish(profile);
        }
        if (conditionType == GraduationConditionType.SW_CERT_COURSE) {
            return studentCourseRepository.findByStudentProfileAndCourseIsSw(profile);
        }
        return toDivisionCategory(conditionType)
                .flatMap(cat -> divisionRepository.findBySchoolAndCategory(profile.getSchool(), cat))
                .map(div -> studentCourseRepository.findByStudentProfileAndAppliedDivisionIn(profile, List.of(div)))
                .orElse(List.of());
    }

    private Optional<DivisionCategory> toDivisionCategory(GraduationConditionType conditionType) {
        try {
            return Optional.of(DivisionCategory.valueOf(conditionType.name()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    // REQUIRED(트랙 지정)이 NONE보다 우선. 같은 course가 두 그룹에 모두 속하면 REQUIRED로 덮어쓴다.
    private Map<Long, String> buildTrackTypeMap(List<RequirementCourseItem> items) {
        Map<Long, String> map = new HashMap<>();
        for (RequirementCourseItem item : items) {
            Long courseId = item.getCourse().getId();
            boolean isTrackSpecific = item.getRequirementCourse().getTrack() != null;
            if (isTrackSpecific || !map.containsKey(courseId)) {
                map.put(courseId, isTrackSpecific ? "REQUIRED" : "NONE");
            }
        }
        return map;
    }

    private CourseInfo toTakenCourseInfo(StudentCourse sc, String trackType) {
        String departmentName = sc.getCourse() != null && sc.getCourse().getOfferingDepartment() != null
                ? sc.getCourse().getOfferingDepartment().getName() : null;
        return CourseInfo.builder()
                .studentCourseId(sc.getId())
                .name(sc.getRawCourseName())
                .departmentName(departmentName)
                .credit(sc.getCredit())
                .semester(sc.getTakenSemester() != null ? semesterName(sc.getTakenSemester()) : null)
                .taken(true)
                .trackType(trackType)
                .build();
    }

    private CourseInfo toNotTakenCourseInfo(RequirementCourseItem item, String trackType) {
        var course = item.getCourse();
        String departmentName = course.getOfferingDepartment() != null
                ? course.getOfferingDepartment().getName() : null;
        return CourseInfo.builder()
                .studentCourseId(null)
                .name(course.getName())
                .departmentName(departmentName)
                .credit(course.getCredit())
                .semester(openedSemesterName(course.getOpenedSemester()))
                .taken(false)
                .trackType(trackType)
                .build();
    }

    private String semesterName(Semester semester) {
        return switch (semester) {
            case FIRST -> "1학기";
            case SECOND -> "2학기";
            case SUMMER -> "여름학기";
            case WINTER -> "겨울학기";
        };
    }

    private String openedSemesterName(OpenedSemester openedSemester) {
        if (openedSemester == null) return null;
        return switch (openedSemester) {
            case FIRST -> "1학기";
            case SECOND -> "2학기";
            case BOTH -> "1·2학기";
        };
    }

    private GraduationConditionType parseDivisionCode(String value) {
        try {
            return GraduationConditionType.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new BaseException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private List<StudentMajor> filterMajors(List<StudentMajor> majors, MajorTypeFilter filter) {
        return switch (filter) {
            case PRIMARY -> majors.stream().filter(m -> m.getMajorType() == MajorType.MAIN).toList();
            case MULTI -> majors.stream().filter(m -> m.getMajorType() == MajorType.DOUBLE).toList();
            case ALL -> majors;
        };
    }

    private GraduationAnalysisSummary requireSummary(StudentMajor studentMajor) {
        return graduationAnalysisSummaryRepository.findByStudentMajor(studentMajor)
                .orElseThrow(() -> new BaseException(ErrorCode.REQUIREMENT_NOT_FOUND));
    }

    private GraduationResponse buildResponse(
            StudentProfile profile,
            GraduationAnalysisSummary baseSummary,
            GraduationAnalysisSummary doubleSummary,
            List<CertResult> certs
    ) {
        Summary summary = Summary.builder()
                .totalCredits(new CreditInfo(baseSummary.getTotalCreditCurrent(), baseSummary.getTotalCreditRequired()))
                .gpa(new GpaInfo(baseSummary.getGpaCurrent(), baseSummary.getGpaRequired()))
                .enrollmentStatus(profile.getEnrollmentStatus())
                .build();

        List<ConditionInfo> conditions = Arrays.stream(GraduationConditionType.values())
                .map(type -> toConditionInfo(type, baseSummary, doubleSummary))
                .toList();

        List<CertInfo> certInfos = certs.stream()
                .map(c -> new CertInfo(c.getCertType().name(), c.getResult().name()))
                .toList();

        return GraduationResponse.builder()
                .summary(summary)
                .conditions(conditions)
                .certs(certInfos)
                .build();
    }

    private ConditionInfo toConditionInfo(
            GraduationConditionType type,
            GraduationAnalysisSummary base,
            GraduationAnalysisSummary extra
    ) {
        int current = type.getCurrentExtractor().applyAsInt(base);
        Integer required = type.getRequiredExtractor() != null
                ? type.getRequiredExtractor().apply(base)
                : null;

        // aggregatable=true인 항목만 복수전공(DOUBLE) 스냅샷 합산 (MAJOR_BASIC/REQUIRED/ELECTIVE)
        if (extra != null && type.isAggregatable()) {
            current += type.getCurrentExtractor().applyAsInt(extra);
            if (required != null) {
                required += type.getRequiredExtractor().apply(extra);
            }
        }

        // required=null이면 기준 없음 → 미달 불가 (GENERAL_ELECTIVE)
        boolean satisfied = required == null || current >= required;

        return ConditionInfo.builder()
                .code(type.name())
                .name(type.getDisplayName())
                .current(current)
                .required(required)
                .unit(type.getUnit())
                .satisfied(satisfied)
                .chartTarget(type.isChartTarget())
                .build();
    }
}