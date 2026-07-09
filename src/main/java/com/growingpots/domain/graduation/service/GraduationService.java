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
import com.growingpots.domain.graduation.dto.response.GraduationResponse.Summary;
import com.growingpots.domain.graduation.dto.response.GraduationResponse.TabSection;
import com.growingpots.domain.graduation.enums.GraduationConditionType;
import com.growingpots.domain.graduation.enums.GraduationSource;
import com.growingpots.domain.graduation.enums.MajorTypeFilter;
import com.growingpots.domain.planner.entity.PlannerVersionItem;
import com.growingpots.domain.planner.repository.PlannerVersionItemRepository;
import com.growingpots.domain.transcript.entity.CertResult;
import com.growingpots.domain.transcript.entity.GraduationAnalysisSummary;
import com.growingpots.domain.transcript.entity.StudentCourse;
import com.growingpots.domain.transcript.entity.enums.CertJudgement;
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
    private final PlannerVersionItemRepository plannerVersionItemRepository;

    private static final List<DivisionCategory> MAJOR_CATEGORIES = List.of(
            DivisionCategory.MAJOR_BASIC, DivisionCategory.MAJOR_REQUIRED, DivisionCategory.MAJOR_ELECTIVE);
    private static final List<DivisionCategory> GE_CATEGORIES = List.of(
            DivisionCategory.REQUIRED_GE, DivisionCategory.DISTRIBUTED_GE, DivisionCategory.FREE_GE);
    private static final List<DivisionCategory> OTHERS_CATEGORIES = List.of(DivisionCategory.GENERAL_ELECTIVE);

    @Transactional(readOnly = true)
    public GraduationResponse getGraduation(Long memberId, MajorTypeFilter majorTypeFilter, GraduationSource source) {
        StudentProfile profile = studentProfileRepository.findWithDetailsByMemberId(memberId)
                .orElseThrow(() -> new BaseException(ErrorCode.STUDENT_PROFILE_NOT_FOUND));

        List<StudentMajor> majors = studentMajorRepository.findWithDepartmentByStudentProfile(profile);

        StudentMajor mainMajor = majors.stream()
                .filter(m -> m.getMajorType() == MajorType.MAIN)
                .findFirst()
                .orElseThrow(() -> new BaseException(ErrorCode.STUDENT_PROFILE_NOT_FOUND));

        GraduationAnalysisSummary mainSummary = requireSummary(mainMajor);
        Optional<StudentMajor> doubleMajorOpt = majors.stream()
                .filter(m -> m.getMajorType() == MajorType.DOUBLE)
                .findFirst();
        GraduationAnalysisSummary doubleSummary = doubleMajorOpt
                .flatMap(graduationAnalysisSummaryRepository::findByStudentMajor)
                .orElse(null);

        List<CertResult> mainCerts = certResultRepository.findByStudentMajor(mainMajor);
        List<CertResult> doubleCerts = doubleMajorOpt
                .map(certResultRepository::findByStudentMajor)
                .orElse(List.of());
        List<CertResult> allCerts = new ArrayList<>(mainCerts);
        allCerts.addAll(doubleCerts);

        // PLANNED: 플래너 선택 버전의 계획 과목 중 이미 이수/수강 중인 과목을 제외한 신규 항목만 사용
        // 플래너가 없거나 신규 항목이 없으면 COMPLETED와 동일한 응답
        List<PlannerVersionItem> allPlannedItems = List.of();
        if (source == GraduationSource.PLANNED) {
            List<Long> alreadyCountedIds = studentCourseRepository.findCourseIdsByStudentProfileAndStatusIn(
                    profile, List.of(CourseStatus.COMPLETED, CourseStatus.IN_PROGRESS));
            Set<Long> alreadyCounted = new HashSet<>(alreadyCountedIds);
            allPlannedItems = plannerVersionItemRepository.findSelectedByStudentProfile(profile).stream()
                    .filter(i -> !alreadyCounted.contains(i.getCourse().getId()))
                    .toList();
        }

        GraduationAnalysisSummary effectiveMainSummary = allPlannedItems.isEmpty() ? mainSummary
                : buildAdjustedSummary(mainSummary, allPlannedItems, mainMajor.getDepartment());
        GraduationAnalysisSummary effectiveDoubleSummary = (doubleSummary == null || allPlannedItems.isEmpty())
                ? doubleSummary
                : buildAdjustedSummary(doubleSummary, allPlannedItems,
                        doubleMajorOpt.map(StudentMajor::getDepartment).orElse(null));

        boolean graduatable = computeGraduatable(effectiveMainSummary, effectiveDoubleSummary, allCerts);

        return switch (majorTypeFilter) {
            case PRIMARY -> buildSingleTabResponse(profile, effectiveMainSummary, MajorTypeFilter.PRIMARY,
                    mainMajor.getDepartment(), graduatable, mainCerts, allPlannedItems);
            case MULTI -> {
                StudentMajor doubleMajor = doubleMajorOpt
                        .orElseThrow(() -> new BaseException(ErrorCode.DOUBLE_MAJOR_NOT_FOUND));
                GraduationAnalysisSummary multiSummary = Optional.ofNullable(effectiveDoubleSummary)
                        .orElseThrow(() -> new BaseException(ErrorCode.REQUIREMENT_NOT_FOUND));
                yield buildSingleTabResponse(profile, multiSummary, MajorTypeFilter.MULTI,
                        doubleMajor.getDepartment(), graduatable, doubleCerts, allPlannedItems);
            }
            case GE, OTHERS -> buildSingleTabResponse(profile, effectiveMainSummary, majorTypeFilter,
                    null, graduatable, mainCerts, allPlannedItems);
            case ALL -> buildAllTabResponse(profile, mainMajor, effectiveMainSummary,
                    doubleMajorOpt.orElse(null), effectiveDoubleSummary, graduatable, mainCerts, allPlannedItems);
        };
    }

    // PRIMARY/MULTI/GE/OTHERS 탭 단건 응답
    private GraduationResponse buildSingleTabResponse(
            StudentProfile profile,
            GraduationAnalysisSummary summary,
            MajorTypeFilter tab,
            Department department,
            boolean graduatable,
            List<CertResult> certs,
            List<PlannerVersionItem> plannedItems
    ) {
        return GraduationResponse.builder()
                .summary(buildSummary(profile, summary))
                .graduatable(graduatable)
                .conditions(buildConditionsForTab(profile, summary, tab, department, plannedItems))
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
            List<CertResult> certs,
            List<PlannerVersionItem> plannedItems
    ) {
        TabSection primarySection = TabSection.builder()
                .majorName(mainMajor.getDepartment().getName())
                .conditions(buildConditionsForTab(profile, mainSummary,
                        MajorTypeFilter.PRIMARY, mainMajor.getDepartment(), plannedItems))
                .build();

        TabSection multiSection = null;
        if (doubleMajor != null && doubleSummary != null) {
            multiSection = TabSection.builder()
                    .majorName(doubleMajor.getDepartment().getName())
                    .conditions(buildConditionsForTab(profile, doubleSummary,
                            MajorTypeFilter.MULTI, doubleMajor.getDepartment(), plannedItems))
                    .build();
        }

        TabSection geSection = TabSection.builder()
                .conditions(buildConditionsForTab(profile, mainSummary, MajorTypeFilter.GE, null, plannedItems))
                .build();

        TabSection othersSection = TabSection.builder()
                .conditions(buildConditionsForTab(profile, mainSummary, MajorTypeFilter.OTHERS, null, plannedItems))
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
    // - 기타 탭(OTHERS): GENERAL_ELECTIVE (영어·SW 미포함)
    private List<ConditionInfo> buildConditionsForTab(
            StudentProfile profile,
            GraduationAnalysisSummary summary,
            MajorTypeFilter tab,
            Department department,
            List<PlannerVersionItem> plannedItems
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
        // OTHERS(기타) 탭에는 포함하지 않음 - 영어·SW는 기타 이수구분이 아님
        if (tab != MajorTypeFilter.OTHERS) {
            List<DivisionCategory> cats = getDivisionCategoriesForTab(tab);
            result.add(buildEnglishConditionInfo(profile, cats, summary, department, plannedItems));
            result.add(buildSwConditionInfo(profile, cats, summary, department, plannedItems));
        }

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
            Department department,
            List<PlannerVersionItem> plannedItems
    ) {
        List<StudentCourse> courses = (department != null)
                ? studentCourseRepository
                        .findByStudentProfileAndCourseIsEnglishAndDivisionCategoryInAndDepartment(
                                profile, categories, department)
                : studentCourseRepository
                        .findByStudentProfileAndCourseIsEnglishAndDivisionCategoryIn(profile, categories);
        int current = courses.size();

        if (!plannedItems.isEmpty()) {
            current += (int) plannedItems.stream()
                    .filter(i -> i.getCourse().isEnglish())
                    .filter(i -> i.getPlannedDivision() != null
                            && categories.contains(i.getPlannedDivision().getCategory()))
                    .filter(i -> department == null
                            || (i.getCourse().getOfferingDepartment() != null
                            && department.getId().equals(i.getCourse().getOfferingDepartment().getId())))
                    .count();
        }

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
            Department department,
            List<PlannerVersionItem> plannedItems
    ) {
        List<StudentCourse> courses = (department != null)
                ? studentCourseRepository
                        .findByStudentProfileAndCourseIsSwAndDivisionCategoryInAndDepartment(
                                profile, categories, department)
                : studentCourseRepository
                        .findByStudentProfileAndCourseIsSwAndDivisionCategoryIn(profile, categories);
        int current = courses.stream().mapToInt(StudentCourse::getCredit).sum();

        if (!plannedItems.isEmpty()) {
            current += plannedItems.stream()
                    .filter(i -> i.getCourse().isSw())
                    .filter(i -> i.getPlannedDivision() != null
                            && categories.contains(i.getPlannedDivision().getCategory()))
                    .filter(i -> department == null
                            || (i.getCourse().getOfferingDepartment() != null
                            && department.getId().equals(i.getCourse().getOfferingDepartment().getId())))
                    .mapToInt(PlannerVersionItem::getCredit).sum();
        }

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

    // PLANNED 모드용: 스냅샷 기반 summary에 계획 과목의 학점 delta를 더해 새 in-memory summary를 반환한다.
    // majorDept: 전공 학점 귀속 판단 기준 (본전공 또는 복수전공). null이면 전공 delta는 0.
    // GPA는 미래 예측 불가이므로 원본 값 유지.
    private GraduationAnalysisSummary buildAdjustedSummary(
            GraduationAnalysisSummary original,
            List<PlannerVersionItem> newPlannedItems,
            Department majorDept
    ) {
        int majorBasicDelta = creditSum(newPlannedItems, DivisionCategory.MAJOR_BASIC, majorDept);
        int majorRequiredDelta = creditSum(newPlannedItems, DivisionCategory.MAJOR_REQUIRED, majorDept);
        int majorElectiveDelta = creditSum(newPlannedItems, DivisionCategory.MAJOR_ELECTIVE, majorDept);
        int requiredGeDelta = creditSum(newPlannedItems, DivisionCategory.REQUIRED_GE, null);
        int distributedGeDelta = creditSum(newPlannedItems, DivisionCategory.DISTRIBUTED_GE, null);
        int freeGeDelta = creditSum(newPlannedItems, DivisionCategory.FREE_GE, null);
        int generalElectiveDelta = newPlannedItems.stream()
                .filter(i -> i.getPlannedDivision() == null
                        || i.getPlannedDivision().getCategory() == DivisionCategory.GENERAL_ELECTIVE)
                .mapToInt(PlannerVersionItem::getCredit).sum();
        int englishDelta = (int) newPlannedItems.stream()
                .filter(i -> i.getCourse().isEnglish()).count();
        int swDelta = newPlannedItems.stream()
                .filter(i -> i.getCourse().isSw())
                .mapToInt(PlannerVersionItem::getCredit).sum();
        int totalCreditDelta = newPlannedItems.stream().mapToInt(PlannerVersionItem::getCredit).sum();

        Integer swCertCurrent = original.getSwCertCurrent();

        return GraduationAnalysisSummary.builder()
                .studentMajor(original.getStudentMajor())
                .totalCreditCurrent(original.getTotalCreditCurrent() + totalCreditDelta)
                .totalCreditRequired(original.getTotalCreditRequired())
                .gpaCurrent(original.getGpaCurrent())
                .gpaRequired(original.getGpaRequired())
                .englishCurrent(original.getEnglishCurrent() + englishDelta)
                .englishRequired(original.getEnglishRequired())
                .swCertCurrent(swCertCurrent != null ? swCertCurrent + swDelta : null)
                .swCertRequired(original.getSwCertRequired())
                .majorBasicCurrent(original.getMajorBasicCurrent() + majorBasicDelta)
                .majorBasicRequired(original.getMajorBasicRequired())
                .majorRequiredCurrent(original.getMajorRequiredCurrent() + majorRequiredDelta)
                .majorRequiredRequired(original.getMajorRequiredRequired())
                .majorElectiveCurrent(original.getMajorElectiveCurrent() + majorElectiveDelta)
                .majorElectiveRequired(original.getMajorElectiveRequired())
                .requiredPlusElectiveCurrent(original.getRequiredPlusElectiveCurrent()
                        + majorRequiredDelta + majorElectiveDelta)
                .requiredPlusElectiveRequired(original.getRequiredPlusElectiveRequired())
                .requiredGeCurrent(original.getRequiredGeCurrent() + requiredGeDelta)
                .requiredGeRequired(original.getRequiredGeRequired())
                .distributedGeCurrent(original.getDistributedGeCurrent() + distributedGeDelta)
                .distributedGeRequired(original.getDistributedGeRequired())
                .freeGeCurrent(original.getFreeGeCurrent() + freeGeDelta)
                .freeGeRequired(original.getFreeGeRequired())
                .generalElectiveCurrent(original.getGeneralElectiveCurrent() + generalElectiveDelta)
                .build();
    }

    // 계획 항목 중 특정 이수구분 카테고리 + 개설학과 조건을 만족하는 항목의 학점 합산
    // dept=null이면 학과 필터 없음 (GE/기타 공통 이수구분용)
    private int creditSum(List<PlannerVersionItem> items, DivisionCategory category, Department dept) {
        return items.stream()
                .filter(i -> i.getPlannedDivision() != null
                        && i.getPlannedDivision().getCategory() == category)
                .filter(i -> dept == null
                        || (i.getCourse().getOfferingDepartment() != null
                        && dept.getId().equals(i.getCourse().getOfferingDepartment().getId())))
                .mapToInt(PlannerVersionItem::getCredit).sum();
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

    // 졸업 가능 여부: 학점 요건 + 비학점 요건(평점, 논문/졸업능력인정 등 인증) 전부 충족 시 true
    // 전공 요건은 각 전공별 독립 확인, 교양/영어/SW는 공통 기준(mainSummary)
    private boolean computeGraduatable(
            GraduationAnalysisSummary mainSummary,
            GraduationAnalysisSummary doubleSummary,
            List<CertResult> allCerts
    ) {
        // 학점 요건
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

        // 평점 요건
        if (mainSummary.getGpaRequired() != null && mainSummary.getGpaCurrent() != null
                && mainSummary.getGpaCurrent().compareTo(mainSummary.getGpaRequired()) < 0) return false;

        // 비학점 인증 요건: 논문·졸업능력인정·영어인증·SW인증·TOPIK 등 FAIL이면 졸업 불가
        // PASS·EXEMPT·NONE(해당없음)은 통과로 간주
        for (CertResult cert : allCerts) {
            if (cert.getResult() == CertJudgement.FAIL) return false;
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