package com.growingpots.domain.graduation.service;

import com.growingpots.domain.graduation.dto.response.GraduationCourseResponse;
import com.growingpots.domain.graduation.dto.response.GraduationCourseResponse.CourseInfo;
import com.growingpots.domain.graduation.dto.response.GraduationCourseResponse.MajorCourses;
import com.growingpots.domain.graduation.dto.response.GraduationResponse;
import com.growingpots.domain.graduation.dto.response.GraduationResponse.AllSections;
import com.growingpots.domain.graduation.dto.response.GraduationResponse.CertInfo;
import com.growingpots.domain.graduation.dto.response.GraduationResponse.ConditionInfo;
import com.growingpots.domain.graduation.dto.response.GraduationResponse.CreditInfo;
import com.growingpots.domain.graduation.dto.response.GraduationResponse.GpaInfo;
import com.growingpots.domain.graduation.dto.response.GraduationResponse.GraduationRequiredSummary;
import com.growingpots.domain.graduation.dto.response.GraduationResponse.Summary;
import com.growingpots.domain.graduation.dto.response.GraduationResponse.TabSection;
import com.growingpots.domain.graduation.enums.GraduationConditionType;
import com.growingpots.domain.graduation.enums.MajorTypeFilter;
import com.growingpots.domain.transcript.entity.CertResult;
import com.growingpots.domain.transcript.entity.GraduationAnalysisSummary;
import com.growingpots.domain.transcript.entity.StudentCourse;
import com.growingpots.domain.transcript.entity.enums.CourseStatus;
import com.growingpots.domain.transcript.entity.enums.Semester;
import com.growingpots.domain.transcript.repository.CertResultRepository;
import com.growingpots.domain.university.entity.Department;
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
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
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

    private static final List<DivisionCategory> MAJOR_CATEGORIES = List.of(
            DivisionCategory.MAJOR_BASIC, DivisionCategory.MAJOR_REQUIRED, DivisionCategory.MAJOR_ELECTIVE);
    private static final List<DivisionCategory> GE_CATEGORIES = List.of(
            DivisionCategory.REQUIRED_GE, DivisionCategory.DISTRIBUTED_GE, DivisionCategory.FREE_GE);
    private static final List<DivisionCategory> OTHERS_CATEGORIES = List.of(DivisionCategory.GENERAL_ELECTIVE);

    @Transactional(readOnly = true)
    public GraduationResponse getGraduation(Long memberId, MajorTypeFilter majorTypeFilter) {
        StudentProfile profile = studentProfileRepository.findWithDetailsByMemberId(memberId)
                .orElseThrow(() -> new BaseException(ErrorCode.STUDENT_PROFILE_NOT_FOUND));

        List<StudentMajor> majors = studentMajorRepository.findWithDepartmentByStudentProfile(profile);

        StudentMajor mainMajor = majors.stream()
                .filter(m -> m.getMajorType() == MajorType.MAIN)
                .findFirst()
                .orElseThrow(() -> new BaseException(ErrorCode.STUDENT_PROFILE_NOT_FOUND));

        // graduatable은 탭 무관 항상 전체 요건 기준으로 계산
        GraduationAnalysisSummary mainSummary = requireSummary(mainMajor);
        Optional<StudentMajor> doubleMajorOpt = majors.stream()
                .filter(m -> m.getMajorType() == MajorType.DOUBLE)
                .findFirst();
        GraduationAnalysisSummary doubleSummary = doubleMajorOpt
                .flatMap(graduationAnalysisSummaryRepository::findByStudentMajor)
                .orElse(null);
        boolean graduatable = computeGraduatable(mainSummary, doubleSummary)
                && judgeGraduationRequired(profile, mainMajor.getDepartment()).satisfied()
                && doubleMajorOpt
                        .map(dm -> judgeGraduationRequired(profile, dm.getDepartment()).satisfied())
                        .orElse(true);

        // TODO(planner-source): source=PLANNED 요청 시 플래너 데이터 기반 계산 구현
        return switch (majorTypeFilter) {
            case PRIMARY -> {
                List<CertResult> certs = certResultRepository.findByStudentMajor(mainMajor);
                yield buildSingleTabResponse(profile, mainSummary, MajorTypeFilter.PRIMARY,
                        mainMajor.getDepartment(), graduatable, certs);
            }
            case MULTI -> {
                StudentMajor doubleMajor = doubleMajorOpt
                        .orElseThrow(() -> new BaseException(ErrorCode.DOUBLE_MAJOR_NOT_FOUND));
                GraduationAnalysisSummary multiSummary = Optional.ofNullable(doubleSummary)
                        .orElseThrow(() -> new BaseException(ErrorCode.REQUIREMENT_NOT_FOUND));
                List<CertResult> certs = certResultRepository.findByStudentMajor(doubleMajor);
                yield buildSingleTabResponse(profile, multiSummary, MajorTypeFilter.MULTI,
                        doubleMajor.getDepartment(), graduatable, certs);
            }
            case GE, OTHERS -> {
                List<CertResult> certs = certResultRepository.findByStudentMajor(mainMajor);
                yield buildSingleTabResponse(profile, mainSummary, majorTypeFilter, null, graduatable, certs);
            }
            case ALL -> {
                List<CertResult> certs = certResultRepository.findByStudentMajor(mainMajor);
                yield buildAllTabResponse(profile, mainMajor, mainSummary,
                        doubleMajorOpt.orElse(null), doubleSummary, graduatable, certs);
            }
        };
    }

    // PRIMARY/MULTI/GE/OTHERS 탭 단건 응답
    private GraduationResponse buildSingleTabResponse(
            StudentProfile profile,
            GraduationAnalysisSummary summary,
            MajorTypeFilter tab,
            Department department,  // PRIMARY/MULTI: 해당 전공 학과, GE/OTHERS: null
            boolean graduatable,
            List<CertResult> certs
    ) {
        return GraduationResponse.builder()
                .summary(buildSummary(profile, summary))
                .graduatable(graduatable)
                .conditions(buildConditionsForTab(profile, summary, tab, department))
                .graduationRequired(department != null ? buildGraduationRequiredSummary(profile, department) : null)
                .sections(null)
                .certs(toCertInfos(certs))
                .build();
    }

    // ALL 탭: 본전공/복수전공/교양/기타 4개 섹션 분리 응답
    private GraduationResponse buildAllTabResponse(
            StudentProfile profile,
            StudentMajor mainMajor,
            GraduationAnalysisSummary mainSummary,
            StudentMajor doubleMajor,
            GraduationAnalysisSummary doubleSummary,
            boolean graduatable,
            List<CertResult> certs
    ) {
        TabSection primarySection = TabSection.builder()
                .majorName(mainMajor.getDepartment().getName())
                .conditions(buildConditionsForTab(profile, mainSummary,
                        MajorTypeFilter.PRIMARY, mainMajor.getDepartment()))
                .graduationRequired(buildGraduationRequiredSummary(profile, mainMajor.getDepartment()))
                .build();

        TabSection multiSection = null;
        if (doubleMajor != null && doubleSummary != null) {
            multiSection = TabSection.builder()
                    .majorName(doubleMajor.getDepartment().getName())
                    .conditions(buildConditionsForTab(profile, doubleSummary,
                            MajorTypeFilter.MULTI, doubleMajor.getDepartment()))
                    .graduationRequired(buildGraduationRequiredSummary(profile, doubleMajor.getDepartment()))
                    .build();
        }

        TabSection geSection = TabSection.builder()
                .conditions(buildConditionsForTab(profile, mainSummary, MajorTypeFilter.GE, null))
                .build();

        TabSection othersSection = TabSection.builder()
                .conditions(buildConditionsForTab(profile, mainSummary, MajorTypeFilter.OTHERS, null))
                .build();

        return GraduationResponse.builder()
                .summary(buildSummary(profile, mainSummary))
                .graduatable(graduatable)
                .conditions(null)
                .sections(AllSections.builder()
                        .primary(primarySection)
                        .multi(multiSection)
                        .ge(geSection)
                        .others(othersSection)
                        .build())
                .certs(toCertInfos(certs))
                .build();
    }

    // 탭별 조건 목록 생성
    // - 전공 탭(PRIMARY/MULTI): MAJOR_* + 영어/SW(전공 이수구분 + 해당 학과 개설 과목)
    // - 교양 탭(GE): REQUIRED_GE/DISTRIBUTED_GE/FREE_GE + 영어/SW(교양 이수구분)
    // - 기타 탭(OTHERS): GENERAL_ELECTIVE + 영어/SW(GENERAL_ELECTIVE 이수구분)
    private List<ConditionInfo> buildConditionsForTab(
            StudentProfile profile,
            GraduationAnalysisSummary summary,
            MajorTypeFilter tab,
            Department department  // PRIMARY/MULTI: 해당 전공 학과, GE/OTHERS: null
    ) {
        List<GraduationConditionType> types = switch (tab) {
            case PRIMARY, MULTI -> List.of(
                    GraduationConditionType.MAJOR_BASIC,
                    GraduationConditionType.MAJOR_REQUIRED,
                    GraduationConditionType.MAJOR_ELECTIVE);
            case GE -> List.of(
                    GraduationConditionType.REQUIRED_GE,
                    GraduationConditionType.DISTRIBUTED_GE,
                    GraduationConditionType.FREE_GE);
            case OTHERS -> List.of(GraduationConditionType.GENERAL_ELECTIVE);
            case ALL -> throw new IllegalStateException("ALL uses buildAllTabResponse");
        };

        List<ConditionInfo> result = new ArrayList<>();
        for (GraduationConditionType type : types) {
            result.add(toConditionInfoFromSnapshot(type, summary));
        }

        // 영어/SW: 이수구분 기준으로 탭 배치.
        // 전공 탭은 offeringDepartment(appliedDepartment 우선)로 본전공/복수전공 구분
        List<DivisionCategory> cats = getDivisionCategoriesForTab(tab);
        result.add(buildEnglishConditionInfo(profile, cats, summary, department));
        result.add(buildSwConditionInfo(profile, cats, summary, department));

        return result;
    }

    private List<DivisionCategory> getDivisionCategoriesForTab(MajorTypeFilter tab) {
        return switch (tab) {
            case PRIMARY, MULTI -> MAJOR_CATEGORIES;
            case GE -> GE_CATEGORIES;
            case OTHERS -> OTHERS_CATEGORIES;
            case ALL -> List.of(DivisionCategory.values());
        };
    }

    // 해당 탭 이수구분(+ 전공 탭이면 개설학과)에 속하는 영어강의 수 기준으로 조건 정보 생성
    private ConditionInfo buildEnglishConditionInfo(
            StudentProfile profile,
            List<DivisionCategory> categories,
            GraduationAnalysisSummary summary,
            Department department  // null이면 학과 필터 없음
    ) {
        List<StudentCourse> courses = (department != null)
                ? studentCourseRepository
                        .findByStudentProfileAndCourseIsEnglishAndDivisionCategoryInAndDepartment(
                                profile, categories, department)
                : studentCourseRepository
                        .findByStudentProfileAndCourseIsEnglishAndDivisionCategoryIn(profile, categories);
        int current = courses.size();
        int required = summary.getEnglishRequired();
        return ConditionInfo.builder()
                .code(GraduationConditionType.ENGLISH_COURSE.name())
                .name(GraduationConditionType.ENGLISH_COURSE.getDisplayName())
                .current(current)
                .required(required)
                .unit(GraduationConditionType.ENGLISH_COURSE.getUnit())
                .satisfied(current >= required)
                .chartTarget(GraduationConditionType.ENGLISH_COURSE.isChartTarget())
                .build();
    }

    // 해당 탭 이수구분(+ 전공 탭이면 개설학과)에 속하는 SW인증강의 학점 합계 기준으로 조건 정보 생성
    private ConditionInfo buildSwConditionInfo(
            StudentProfile profile,
            List<DivisionCategory> categories,
            GraduationAnalysisSummary summary,
            Department department  // null이면 학과 필터 없음
    ) {
        List<StudentCourse> courses = (department != null)
                ? studentCourseRepository
                        .findByStudentProfileAndCourseIsSwAndDivisionCategoryInAndDepartment(
                                profile, categories, department)
                : studentCourseRepository
                        .findByStudentProfileAndCourseIsSwAndDivisionCategoryIn(profile, categories);
        int current = courses.stream().mapToInt(StudentCourse::getCredit).sum();
        Integer required = summary.getSwCertRequired();
        boolean satisfied = required == null || current >= required;
        return ConditionInfo.builder()
                .code(GraduationConditionType.SW_CERT_COURSE.name())
                .name(GraduationConditionType.SW_CERT_COURSE.getDisplayName())
                .current(current)
                .required(required)
                .unit(GraduationConditionType.SW_CERT_COURSE.getUnit())
                .satisfied(satisfied)
                .chartTarget(GraduationConditionType.SW_CERT_COURSE.isChartTarget())
                .build();
    }

    private ConditionInfo toConditionInfoFromSnapshot(GraduationConditionType type, GraduationAnalysisSummary summary) {
        int current = type.getCurrentExtractor().applyAsInt(summary);
        Integer required = type.getRequiredExtractor() != null ? type.getRequiredExtractor().apply(summary) : null;
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

    private Summary buildSummary(StudentProfile profile, GraduationAnalysisSummary summary) {
        return Summary.builder()
                .totalCredits(new CreditInfo(summary.getTotalCreditCurrent(), summary.getTotalCreditRequired()))
                .gpa(new GpaInfo(summary.getGpaCurrent(), summary.getGpaRequired()))
                .enrollmentStatus(profile.getEnrollmentStatus())
                .build();
    }

    private List<CertInfo> toCertInfos(List<CertResult> certs) {
        return certs.stream()
                .map(c -> new CertInfo(c.getCertType().name(), c.getResult().name()))
                .toList();
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
                .map(major -> buildMajorCourses(profile, major, conditionType, majorTypeFilter))
                .toList();

        return GraduationCourseResponse.builder()
                .divisionCode(conditionType.name())
                .divisionName(conditionType.getDisplayName())
                .majors(majorCoursesList)
                .build();
    }

    private MajorCourses buildMajorCourses(StudentProfile profile, StudentMajor major,
            GraduationConditionType conditionType, MajorTypeFilter filter) {
        if (conditionType == GraduationConditionType.GRADUATION_REQUIRED) {
            return buildGraduationRequiredMajorCourses(profile, major);
        }

        GraduationAnalysisSummary summary = requireSummary(major);

        // 영어/SW: major.getDepartment() 전달 → fetchTakenCourses에서 PRIMARY/MULTI면 학과 필터 적용
        List<StudentCourse> takenCourses = fetchTakenCourses(profile, conditionType, filter, major.getDepartment());

        // 영어/SW는 실시간 목록 집계, 나머지는 스냅샷 값 사용
        int current;
        Integer required;
        if (conditionType == GraduationConditionType.ENGLISH_COURSE) {
            current = takenCourses.size();
            required = summary.getEnglishRequired();
        } else if (conditionType == GraduationConditionType.SW_CERT_COURSE) {
            current = takenCourses.stream().mapToInt(StudentCourse::getCredit).sum();
            required = summary.getSwCertRequired();
        } else {
            current = conditionType.getCurrentExtractor().applyAsInt(summary);
            required = conditionType.getRequiredExtractor() != null
                    ? conditionType.getRequiredExtractor().apply(summary) : null;
        }
        boolean satisfied = required == null || current >= required;

        int admissionYear = profile.getAdmissionYear();
        Optional<Division> divisionOpt = toDivisionCategory(conditionType)
                .flatMap(cat -> divisionRepository.findBySchoolAndCategory(profile.getSchool(), cat));

        List<RequirementCourse> requirementCourses = divisionOpt
                .map(div -> requirementCourseRepository.findApplicable(
                        major.getDepartment(), div, admissionYear))
                .orElse(List.of());
        boolean hasRequiredList = !requirementCourses.isEmpty();

        List<RequirementCourseItem> allItems = hasRequiredList
                ? requirementCourseItemRepository.findWithCourseByRequirementCourseIn(requirementCourses)
                : List.of();

        Set<Long> takenCourseIds = new HashSet<>();
        List<CourseInfo> courses = new ArrayList<>();

        for (StudentCourse sc : takenCourses) {
            if (sc.getCourse() != null) {
                takenCourseIds.add(sc.getCourse().getId());
            }
            courses.add(toTakenCourseInfo(sc));
        }

        // 미이수 과목: RequirementCourseItem 중 이수하지 않은 것. courseId 기준 중복 제거.
        if (hasRequiredList) {
            Set<Long> addedIds = new HashSet<>();
            for (RequirementCourseItem item : allItems) {
                Long courseId = item.getCourse().getId();
                if (!takenCourseIds.contains(courseId) && addedIds.add(courseId)) {
                    courses.add(toNotTakenCourseInfo(item));
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

    // 학과 자체의 독립 졸업요건(division 기반 아님, 예: 스포츠의학과 졸업필수) 드릴다운 응답.
    // 하위조건이 여러 개일 수 있어 current/required는 "만족한 조건 수/전체 조건 수"로 요약하고,
    // 조건별 상세는 unmetDescriptions에 문구로 담는다. 과목 리스트는 모든 하위조건에 연결된
    // 과목을 하나로 합쳐서 보여준다(전문실기1~6 + 맨손체조가 한 리스트에 섞여 나옴).
    private MajorCourses buildGraduationRequiredMajorCourses(StudentProfile profile, StudentMajor major) {
        GraduationRequiredJudgement judgement = judgeGraduationRequired(profile, major.getDepartment());
        boolean hasRequiredList = !judgement.items().isEmpty();

        Set<Long> takenCourseIds = new HashSet<>();
        List<CourseInfo> courses = new ArrayList<>();
        for (StudentCourse sc : judgement.takenCourses()) {
            courses.add(toTakenCourseInfo(sc));
            takenCourseIds.add(sc.getCourse().getId());
        }

        Set<Long> addedIds = new HashSet<>();
        for (RequirementCourseItem item : judgement.items()) {
            Long courseId = item.getCourse().getId();
            if (!takenCourseIds.contains(courseId) && addedIds.add(courseId)) {
                courses.add(toNotTakenCourseInfo(item));
            }
        }
        courses.sort(Comparator.comparing(CourseInfo::getName));

        return MajorCourses.builder()
                .majorType(major.getMajorType().name())
                .departmentName(major.getDepartment().getName())
                .current(judgement.totalRequirementCount() - judgement.unmetDescriptions().size())
                .required(judgement.totalRequirementCount())
                .satisfied(judgement.satisfied())
                .hasRequiredList(hasRequiredList)
                .unmetDescriptions(judgement.unmetDescriptions())
                .courses(courses)
                .build();
    }

    // 홈 화면 요약용. 해당 학과에 독립 졸업요건 자체가 없으면(대부분의 학과) null을 반환해
    // FE가 이 섹션을 아예 안 보여줄 수 있게 한다.
    private GraduationRequiredSummary buildGraduationRequiredSummary(StudentProfile profile, Department department) {
        GraduationRequiredJudgement judgement = judgeGraduationRequired(profile, department);
        if (judgement.items().isEmpty()) {
            return null;
        }
        return GraduationRequiredSummary.builder()
                .satisfied(judgement.satisfied())
                .unmetDescriptions(judgement.unmetDescriptions())
                .build();
    }

    // 학과의 독립 졸업요건(division=null인 RequirementCourse들)을 학생 이수내역과 대조해 판정한다.
    // 해당 학과에 이런 요건이 없으면(대부분의 학과) 항상 satisfied=true, 빈 리스트를 반환한다.
    private GraduationRequiredJudgement judgeGraduationRequired(StudentProfile profile, Department department) {
        List<RequirementCourse> requirementCourses = requirementCourseRepository
                .findGraduationRequiredByDepartment(department, profile.getAdmissionYear());
        if (requirementCourses.isEmpty()) {
            return new GraduationRequiredJudgement(true, List.of(), 0, List.of(), List.of());
        }

        List<RequirementCourseItem> allItems =
                requirementCourseItemRepository.findWithCourseByRequirementCourseIn(requirementCourses);
        Map<Long, List<RequirementCourseItem>> itemsByRequirement = allItems.stream()
                .collect(Collectors.groupingBy(item -> item.getRequirementCourse().getId()));

        List<StudentCourse> studentCourses = studentCourseRepository.findWithCourseByStudentProfile(profile);
        Set<Long> requirementCourseIds = allItems.stream()
                .map(item -> item.getCourse().getId())
                .collect(Collectors.toSet());
        List<StudentCourse> takenCourses = studentCourses.stream()
                .filter(sc -> sc.getCourse() != null
                        && sc.getStatus() == CourseStatus.COMPLETED
                        && requirementCourseIds.contains(sc.getCourse().getId()))
                .toList();
        Map<Long, Integer> completedCreditByCourseId = takenCourses.stream()
                .collect(Collectors.toMap(sc -> sc.getCourse().getId(), StudentCourse::getCredit, (a, b) -> a));

        // minCredit이 있으면 학점 합으로, minCount가 있으면 이수 과목 수로 판정한다(둘 다 있는 행은
        // 현재 데이터엔 없지만, minCredit을 우선한다).
        List<String> unmetDescriptions = new ArrayList<>();
        for (RequirementCourse rc : requirementCourses) {
            List<RequirementCourseItem> items = itemsByRequirement.getOrDefault(rc.getId(), List.of());
            boolean byCredit = rc.getMinCredit() > 0;
            int current = byCredit
                    ? items.stream()
                            .mapToInt(item -> completedCreditByCourseId.getOrDefault(item.getCourse().getId(), 0))
                            .sum()
                    : (int) items.stream()
                            .filter(item -> completedCreditByCourseId.containsKey(item.getCourse().getId()))
                            .count();
            int required = byCredit ? rc.getMinCredit() : rc.getMinCount();
            if (current < required) {
                String unit = byCredit ? "학점" : "과목";
                unmetDescriptions.add("[" + rc.getName() + "] " + current + "/" + required + unit + " 이수완료");
            }
        }

        return new GraduationRequiredJudgement(
                unmetDescriptions.isEmpty(), unmetDescriptions, requirementCourses.size(), allItems, takenCourses);
    }

    private record GraduationRequiredJudgement(
            boolean satisfied,
            List<String> unmetDescriptions,
            int totalRequirementCount,
            List<RequirementCourseItem> items,
            List<StudentCourse> takenCourses
    ) {
    }

    // 영어/SW: 전공 탭(PRIMARY/MULTI)은 이수구분 + 개설학과 기준 필터링으로 본전공/복수전공 구분
    //          교양/기타 탭은 이수구분 기준만 적용
    //          ALL은 전체 영어/SW 조회 (기존 동작 유지)
    private List<StudentCourse> fetchTakenCourses(StudentProfile profile,
            GraduationConditionType conditionType, MajorTypeFilter filter, Department department) {
        if (conditionType == GraduationConditionType.ENGLISH_COURSE) {
            if (filter == MajorTypeFilter.ALL) {
                return studentCourseRepository.findByStudentProfileAndCourseIsEnglish(profile);
            }
            List<DivisionCategory> cats = getDivisionCategoriesForTab(filter);
            if (filter == MajorTypeFilter.PRIMARY || filter == MajorTypeFilter.MULTI) {
                return studentCourseRepository
                        .findByStudentProfileAndCourseIsEnglishAndDivisionCategoryInAndDepartment(
                                profile, cats, department);
            }
            return studentCourseRepository
                    .findByStudentProfileAndCourseIsEnglishAndDivisionCategoryIn(profile, cats);
        }
        if (conditionType == GraduationConditionType.SW_CERT_COURSE) {
            if (filter == MajorTypeFilter.ALL) {
                return studentCourseRepository.findByStudentProfileAndCourseIsSw(profile);
            }
            List<DivisionCategory> cats = getDivisionCategoriesForTab(filter);
            if (filter == MajorTypeFilter.PRIMARY || filter == MajorTypeFilter.MULTI) {
                return studentCourseRepository
                        .findByStudentProfileAndCourseIsSwAndDivisionCategoryInAndDepartment(
                                profile, cats, department);
            }
            return studentCourseRepository
                    .findByStudentProfileAndCourseIsSwAndDivisionCategoryIn(profile, cats);
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

    private CourseInfo toTakenCourseInfo(StudentCourse sc) {
        String departmentName = sc.getCourse() != null && sc.getCourse().getOfferingDepartment() != null
                ? sc.getCourse().getOfferingDepartment().getName() : null;
        return CourseInfo.builder()
                .studentCourseId(sc.getId())
                .name(sc.getRawCourseName())
                .departmentName(departmentName)
                .credit(sc.getCredit())
                .semester(sc.getTakenSemester() != null ? semesterName(sc.getTakenSemester()) : null)
                .taken(true)
                .build();
    }

    private CourseInfo toNotTakenCourseInfo(RequirementCourseItem item) {
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

    // GE/OTHERS는 전공 무관 학생 전체 공통값이므로 본전공 기준으로 조회
    private List<StudentMajor> filterMajors(List<StudentMajor> majors, MajorTypeFilter filter) {
        return switch (filter) {
            case PRIMARY -> majors.stream().filter(m -> m.getMajorType() == MajorType.MAIN).toList();
            case MULTI -> majors.stream().filter(m -> m.getMajorType() == MajorType.DOUBLE).toList();
            case ALL -> majors;
            case GE, OTHERS -> majors.stream().filter(m -> m.getMajorType() == MajorType.MAIN).toList();
        };
    }

    // 졸업 가능 여부: required가 있는 모든 항목이 충족됐는지 스냅샷 기준으로 확인
    // 전공 요건은 각 전공별 독립 확인, 교양/영어/SW는 공통 기준(mainSummary)
    private boolean computeGraduatable(
            GraduationAnalysisSummary mainSummary,
            GraduationAnalysisSummary doubleSummary
    ) {
        if (!isMajorRequirementMet(mainSummary)) return false;
        if (doubleSummary != null && !isMajorRequirementMet(doubleSummary)) return false;

        if (mainSummary.getRequiredGeCurrent() < mainSummary.getRequiredGeRequired()) return false;
        if (mainSummary.getDistributedGeCurrent() < mainSummary.getDistributedGeRequired()) return false;
        if (mainSummary.getFreeGeCurrent() < mainSummary.getFreeGeRequired()) return false;

        if (mainSummary.getEnglishCurrent() < mainSummary.getEnglishRequired()) return false;

        Integer swRequired = mainSummary.getSwCertRequired();
        if (swRequired != null) {
            int swCurrent = mainSummary.getSwCertCurrent() != null ? mainSummary.getSwCertCurrent() : 0;
            if (swCurrent < swRequired) return false;
        }

        return true;
    }

    private boolean isMajorRequirementMet(GraduationAnalysisSummary summary) {
        return summary.getMajorBasicCurrent() >= summary.getMajorBasicRequired()
                && summary.getMajorRequiredCurrent() >= summary.getMajorRequiredRequired()
                && summary.getMajorElectiveCurrent() >= summary.getMajorElectiveRequired();
    }

    private GraduationAnalysisSummary requireSummary(StudentMajor studentMajor) {
        return graduationAnalysisSummaryRepository.findByStudentMajor(studentMajor)
                .orElseThrow(() -> new BaseException(ErrorCode.REQUIREMENT_NOT_FOUND));
    }
}