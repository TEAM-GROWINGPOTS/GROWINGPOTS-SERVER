package com.growingpots.domain.graduation.service;

import com.growingpots.domain.graduation.dto.response.GraduationCourseResponse;
import com.growingpots.domain.graduation.dto.response.GraduationCourseResponse.AreaInfo;
import com.growingpots.domain.graduation.dto.response.GraduationCourseResponse.CourseInfo;
import com.growingpots.domain.graduation.dto.response.GraduationCourseResponse.MajorCourses;
import com.growingpots.domain.graduation.dto.response.GraduationResponse;
import com.growingpots.domain.graduation.dto.response.GraduationResponse.AllSections;
import com.growingpots.domain.graduation.dto.response.GraduationResponse.CertInfo;
import com.growingpots.domain.graduation.dto.response.GraduationResponse.ConditionInfo;
import com.growingpots.domain.graduation.dto.response.GraduationResponse.CreditInfo;
import com.growingpots.domain.graduation.dto.response.GraduationResponse.GpaInfo;
import com.growingpots.domain.graduation.dto.response.GraduationResponse.GraduationRequiredSummary;
import com.growingpots.domain.graduation.dto.response.GraduationResponse.RequirementProgress;
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
import com.growingpots.domain.university.entity.Course;
import com.growingpots.domain.university.entity.Department;
import com.growingpots.domain.university.entity.enums.DivisionCategory;
import com.growingpots.domain.transcript.repository.GraduationAnalysisSummaryRepository;
import com.growingpots.domain.transcript.repository.StudentCourseRepository;
import com.growingpots.domain.university.entity.Division;
import com.growingpots.domain.university.entity.GeArea;
import com.growingpots.domain.university.entity.RequirementCourse;
import com.growingpots.domain.university.entity.RequirementCourseItem;
import com.growingpots.domain.university.entity.enums.OpenedSemester;
import com.growingpots.domain.university.repository.CourseRepository;
import com.growingpots.domain.university.repository.DivisionRepository;
import com.growingpots.domain.university.repository.GeAreaRepository;
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
    private final CourseRepository courseRepository;
    private final DivisionRepository divisionRepository;
    private final RequirementCourseRepository requirementCourseRepository;
    private final RequirementCourseItemRepository requirementCourseItemRepository;
    private final PlannerVersionItemRepository plannerVersionItemRepository;
    private final GeAreaRepository geAreaRepository;

    private static final List<DivisionCategory> MAJOR_CATEGORIES = List.of(
            DivisionCategory.MAJOR_BASIC, DivisionCategory.MAJOR_REQUIRED, DivisionCategory.MAJOR_ELECTIVE);
    private static final List<DivisionCategory> GE_CATEGORIES = List.of(
            DivisionCategory.REQUIRED_GE, DivisionCategory.DISTRIBUTED_GE, DivisionCategory.FREE_GE);
    private static final List<DivisionCategory> OTHERS_CATEGORIES = List.of(DivisionCategory.GENERAL_ELECTIVE);

    private static final int DISTRIBUTED_GE_AREA_YEAR_CUTOFF = 2024;
    private static final int DISTRIBUTED_GE_REQUIRED_AREA_COUNT = 3;
    private static final List<String> DISTRIBUTED_GE_AREA_CODES =
            List.of("AREA_1", "AREA_2", "AREA_3", "AREA_4", "AREA_5");

    // 응답 조합 목적의 내부 구분자. 공개 API의 MajorTypeFilter(ALL/GE/OTHERS)와 별개로, "전공 하나"를
    // 본전공/복수전공 구분 없이 균일하게 다루기 위해 MAJOR로 통일한다(예전 PRIMARY/MULTI 이분법을 대체).
    private enum ConditionsTab { MAJOR, GE, OTHERS }

    @Transactional(readOnly = true)
    public GraduationResponse getGraduation(
            Long memberId, MajorTypeFilter majorTypeFilter, Long studentMajorId, GraduationSource source) {
        StudentProfile profile = studentProfileRepository.findWithDetailsByMemberId(memberId)
                .orElseThrow(() -> new BaseException(ErrorCode.STUDENT_PROFILE_NOT_FOUND));

        List<StudentMajor> majors = studentMajorRepository.findWithDepartmentByStudentProfile(profile);

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

        // 전공별(본전공 + 복수전공 몇 개든 전부) 스냅샷/졸업필수 판정/PLANNED 반영을 한 번씩만 계산해
        // 재사용한다. 예전엔 본전공+복수전공 1개로 고정돼 있었지만, 복수전공을 여러 개 가진 학생도 있어
        // 리스트로 다룬다.
        List<PlannerVersionItem> plannedItemsForJudgement = allPlannedItems;
        List<StudentMajorContext> majorContexts = majors.stream()
                .map(major -> {
                    GraduationAnalysisSummary summary = requireSummary(major);
                    GraduationRequiredJudgement judgement =
                            judgeGraduationRequired(profile, major.getDepartment(), plannedItemsForJudgement);
                    GraduationAnalysisSummary effectiveSummary = plannedItemsForJudgement.isEmpty() ? summary
                            : buildAdjustedSummary(summary, plannedItemsForJudgement, major.getDepartment());
                    List<CertResult> certs = certResultRepository.findByStudentMajor(major);
                    return new StudentMajorContext(major, effectiveSummary, judgement, certs);
                })
                .toList();

        DistributedGeAreaResult geAreaResult = null;
        if (profile.getAdmissionYear() >= DISTRIBUTED_GE_AREA_YEAR_CUTOFF) {
            List<StudentCourse> distCourses = fetchDistributedGeCourses(profile);
            geAreaResult = computeDistributedGeAreas(distCourses, allPlannedItems, profile);
        }

        List<CertResult> allCerts = majorContexts.stream().flatMap(c -> c.certs().stream()).toList();
        boolean graduatable = computeGraduatable(majorContexts, allCerts, geAreaResult);

        if (studentMajorId != null) {
            StudentMajorContext target = findMajorContextByStudentMajorId(majorContexts, studentMajorId);
            return buildSingleTabResponse(profile, target.effectiveSummary(), ConditionsTab.MAJOR,
                    target.major().getDepartment(), target.judgement(), graduatable, target.certs(),
                    allPlannedItems, null);
        }

        StudentMajorContext mainContext = mainContext(majorContexts);
        return switch (majorTypeFilter) {
            case GE, OTHERS -> buildSingleTabResponse(profile, mainContext.effectiveSummary(),
                    majorTypeFilter == MajorTypeFilter.GE ? ConditionsTab.GE : ConditionsTab.OTHERS,
                    null, null, graduatable, mainContext.certs(), allPlannedItems, geAreaResult);
            case ALL -> buildAllTabResponse(profile, majorContexts, graduatable, allPlannedItems, geAreaResult);
        };
    }

    // 전공 하나(본전공이든 복수전공이든 구분 없이) 또는 GE/OTHERS 탭 단건 응답
    private GraduationResponse buildSingleTabResponse(
            StudentProfile profile,
            GraduationAnalysisSummary summary,
            ConditionsTab tab,
            Department department,  // 전공 탭: 해당 전공 학과, GE/OTHERS: null
            GraduationRequiredJudgement judgement,  // 전공 탭만 값 있음, GE/OTHERS는 null
            boolean graduatable,
            List<CertResult> certs,
            List<PlannerVersionItem> plannedItems,
            DistributedGeAreaResult geAreaResult  // GE/OTHERS 탭만 전달, 전공 탭은 null
    ) {
        return GraduationResponse.builder()
                .summary(buildSummary(profile, summary))
                .graduatable(graduatable)
                .conditions(buildConditionsForTab(profile, summary, tab, department, plannedItems, geAreaResult))
                .graduationRequired(toGraduationRequiredSummary(judgement))
                .sections(null)
                .certs(toCertInfos(certs))
                .build();
    }

    // ALL 탭: 보유 전공 전부(본전공 + 복수전공 몇 개든) + 교양 + 기타 섹션 분리 응답
    private GraduationResponse buildAllTabResponse(
            StudentProfile profile,
            List<StudentMajorContext> majorContexts,
            boolean graduatable,
            List<PlannerVersionItem> plannedItems,
            DistributedGeAreaResult geAreaResult
    ) {
        List<TabSection> majorSections = majorContexts.stream()
                .map(ctx -> TabSection.builder()
                        .majorName(ctx.major().getDepartment().getName())
                        .majorType(ctx.major().getMajorType().name())
                        .conditions(buildConditionsForTab(profile, ctx.effectiveSummary(),
                                ConditionsTab.MAJOR, ctx.major().getDepartment(), plannedItems, null))
                        .graduationRequired(toGraduationRequiredSummary(ctx.judgement()))
                        .build())
                .toList();

        StudentMajorContext mainContext = mainContext(majorContexts);

        TabSection geSection = TabSection.builder()
                .conditions(buildConditionsForTab(profile, mainContext.effectiveSummary(),
                        ConditionsTab.GE, null, plannedItems, geAreaResult))
                .build();

        TabSection othersSection = TabSection.builder()
                .conditions(buildConditionsForTab(profile, mainContext.effectiveSummary(),
                        ConditionsTab.OTHERS, null, plannedItems, null))
                .build();

        return GraduationResponse.builder()
                .summary(buildSummary(profile, mainContext.effectiveSummary()))
                .graduatable(graduatable)
                .conditions(null)
                .sections(AllSections.builder()
                        .majors(majorSections)
                        .ge(geSection)
                        .others(othersSection)
                        .build())
                .certs(toCertInfos(mainContext.certs()))
                .build();
    }

    private StudentMajorContext mainContext(List<StudentMajorContext> majorContexts) {
        return majorContexts.stream()
                .filter(c -> c.major().getMajorType() == MajorType.MAIN)
                .findFirst()
                .orElseThrow(() -> new BaseException(ErrorCode.STUDENT_PROFILE_NOT_FOUND));
    }

    private StudentMajorContext findMajorContextByStudentMajorId(
            List<StudentMajorContext> majorContexts, Long studentMajorId) {
        return majorContexts.stream()
                .filter(c -> c.major().getId().equals(studentMajorId))
                .findFirst()
                .orElseThrow(() -> new BaseException(ErrorCode.STUDENT_MAJOR_NOT_FOUND));
    }

    // getGraduation() 안에서 전공 하나(StudentMajor)에 대해 필요한 값들을 한 번씩만 계산해 재사용하기
    // 위한 묶음. effectiveSummary는 PLANNED 반영 후 값(계획 없으면 스냅샷 그대로).
    private record StudentMajorContext(
            StudentMajor major,
            GraduationAnalysisSummary effectiveSummary,
            GraduationRequiredJudgement judgement,
            List<CertResult> certs
    ) {
    }

    // 탭별 조건 목록 생성
    // - 전공 탭(MAJOR, 본전공/복수전공 구분 없이 전공 하나): MAJOR_* + 영어/SW(전공 이수구분 + 해당 학과 개설 과목)
    // - 교양 탭(GE): REQUIRED_GE/DISTRIBUTED_GE/FREE_GE + 영어/SW(교양 이수구분)
    // - 기타 탭(OTHERS): GENERAL_ELECTIVE (영어·SW 미포함)
    private List<ConditionInfo> buildConditionsForTab(
            StudentProfile profile,
            GraduationAnalysisSummary summary,
            ConditionsTab tab,
            Department department,
            List<PlannerVersionItem> plannedItems,
            DistributedGeAreaResult geAreaResult
    ) {
        List<GraduationConditionType> types = switch (tab) {
            case MAJOR -> List.of(
                    GraduationConditionType.MAJOR_BASIC,
                    GraduationConditionType.MAJOR_REQUIRED,
                    GraduationConditionType.MAJOR_ELECTIVE);
            case GE -> List.of(
                    GraduationConditionType.REQUIRED_GE,
                    GraduationConditionType.DISTRIBUTED_GE,
                    GraduationConditionType.FREE_GE);
            case OTHERS -> List.of(GraduationConditionType.GENERAL_ELECTIVE);
        };

        List<ConditionInfo> result = new ArrayList<>();
        for (GraduationConditionType type : types) {
            if (type == GraduationConditionType.DISTRIBUTED_GE) {
                result.add(buildDistributedGeConditionInfo(summary, geAreaResult));
            } else {
                result.add(toConditionInfoFromSnapshot(type, summary));
            }
        }

        // 영어/SW: 이수구분 기준으로 탭 배치.
        // 전공 탭은 offeringDepartment(appliedDepartment 우선)로 해당 전공 학과만 필터링
        // OTHERS(기타) 탭에는 포함하지 않음 - 영어·SW는 기타 이수구분이 아님
        if (tab != ConditionsTab.OTHERS) {
            List<DivisionCategory> cats = getDivisionCategoriesForTab(tab);
            result.add(buildEnglishConditionInfo(profile, cats, summary, department, plannedItems));
            result.add(buildSwConditionInfo(profile, cats, summary, department, plannedItems));
        }

        return result;
    }

    private List<DivisionCategory> getDivisionCategoriesForTab(ConditionsTab tab) {
        return switch (tab) {
            case MAJOR -> MAJOR_CATEGORIES;
            case GE -> GE_CATEGORIES;
            case OTHERS -> OTHERS_CATEGORIES;
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
        // GENERAL_ELECTIVE(기타)는 요구 학점 자체가 없어(required=null) 항상 satisfied=true로 계산되던
        // 걸 요청에 따라 무조건 false로 고정한다 - 졸업 요건이 아니라 참고용 집계라 "충족" 배지를 아예
        // 안 보여주기 위함.
        boolean satisfied = type != GraduationConditionType.GENERAL_ELECTIVE && (required == null || current >= required);
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

    private ConditionInfo buildDistributedGeConditionInfo(
            GraduationAnalysisSummary summary, DistributedGeAreaResult geAreaResult) {
        int current = summary.getDistributedGeCurrent();
        Integer required = summary.getDistributedGeRequired();
        boolean creditSatisfied = required == null || current >= required;
        boolean satisfied = creditSatisfied && (geAreaResult == null || geAreaResult.satisfied());
        return ConditionInfo.builder()
                .code(GraduationConditionType.DISTRIBUTED_GE.name())
                .name(GraduationConditionType.DISTRIBUTED_GE.getDisplayName())
                .current(current)
                .required(required)
                .unit(GraduationConditionType.DISTRIBUTED_GE.getUnit())
                .satisfied(satisfied)
                .chartTarget(GraduationConditionType.DISTRIBUTED_GE.isChartTarget())
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
    public GraduationCourseResponse getCoursesByDivision(
            Long memberId, String divisionCodeStr, MajorTypeFilter majorTypeFilter, Long studentMajorId) {
        GraduationConditionType conditionType = parseDivisionCode(divisionCodeStr);

        StudentProfile profile = studentProfileRepository.findWithDetailsByMemberId(memberId)
                .orElseThrow(() -> new BaseException(ErrorCode.STUDENT_PROFILE_NOT_FOUND));

        List<StudentMajor> majors = studentMajorRepository.findWithDepartmentByStudentProfile(profile);

        // ENGLISH_COURSE/SW_CERT_COURSE는 이수구분이 아닌 course 플래그 기반이라 처리가 다르다.
        // studentMajorId 없이 OTHERS: 졸업현황 OTHERS 섹션에 영어·SW 조건이 없으므로 빈 응답
        // studentMajorId 없이 ALL: 탭·학과 구분 없이 전체 합산해서 단일 항목으로 반환
        // studentMajorId 있으면(전공 하나 지정) 아래 공통 흐름을 그대로 탄다.
        if (studentMajorId == null && (conditionType == GraduationConditionType.ENGLISH_COURSE
                || conditionType == GraduationConditionType.SW_CERT_COURSE)) {
            if (majorTypeFilter == MajorTypeFilter.OTHERS) {
                return GraduationCourseResponse.builder()
                        .divisionCode(conditionType.name())
                        .divisionName(conditionType.getDisplayName())
                        .majors(List.of())
                        .build();
            }
            if (majorTypeFilter == MajorTypeFilter.ALL) {
                return buildMergedFlagCourseResponse(profile, majors, conditionType);
            }
        }

        List<StudentMajor> targetMajors;
        ConditionsTab tab;
        if (studentMajorId != null) {
            targetMajors = List.of(findMajorByStudentMajorId(majors, studentMajorId));
            tab = ConditionsTab.MAJOR;
        } else {
            targetMajors = switch (majorTypeFilter) {
                case ALL -> majors;
                case GE, OTHERS -> majors.stream().filter(m -> m.getMajorType() == MajorType.MAIN).toList();
            };
            tab = switch (majorTypeFilter) {
                case ALL -> ConditionsTab.MAJOR;
                case GE -> ConditionsTab.GE;
                case OTHERS -> ConditionsTab.OTHERS;
            };
        }

        List<MajorCourses> majorCoursesList = targetMajors.stream()
                .map(major -> buildMajorCourses(profile, major, conditionType, tab))
                .toList();

        return GraduationCourseResponse.builder()
                .divisionCode(conditionType.name())
                .divisionName(conditionType.getDisplayName())
                .majors(majorCoursesList)
                .build();
    }

    private StudentMajor findMajorByStudentMajorId(List<StudentMajor> majors, Long studentMajorId) {
        return majors.stream()
                .filter(m -> m.getId().equals(studentMajorId))
                .findFirst()
                .orElseThrow(() -> new BaseException(ErrorCode.STUDENT_MAJOR_NOT_FOUND));
    }

    // ENGLISH_COURSE/SW_CERT_COURSE + majorType=ALL 전용.
    // 탭·학과 구분 없이 학생의 전체 영어/SW 강의를 하나로 합산해 단일 MajorCourses로 반환한다.
    // required는 본전공 스냅샷 기준(영어/SW 요건은 학과 공통이므로 본전공 summary에서 읽는다).
    // majorType=null: 특정 전공에 귀속되지 않는 전체 합산임을 명시.
    private GraduationCourseResponse buildMergedFlagCourseResponse(
            StudentProfile profile,
            List<StudentMajor> majors,
            GraduationConditionType conditionType
    ) {
        StudentMajor mainMajor = majors.stream()
                .filter(m -> m.getMajorType() == MajorType.MAIN)
                .findFirst()
                .orElseThrow(() -> new BaseException(ErrorCode.STUDENT_PROFILE_NOT_FOUND));
        GraduationAnalysisSummary summary = requireSummary(mainMajor);

        List<StudentCourse> takenCourses;
        int current;
        Integer required;
        if (conditionType == GraduationConditionType.ENGLISH_COURSE) {
            takenCourses = studentCourseRepository.findByStudentProfileAndCourseIsEnglish(profile);
            current = takenCourses.size();
            required = summary.getEnglishRequired();
        } else {
            takenCourses = studentCourseRepository.findByStudentProfileAndCourseIsSw(profile);
            current = takenCourses.stream().mapToInt(StudentCourse::getCredit).sum();
            required = summary.getSwCertRequired();
        }
        boolean satisfied = required == null || current >= required;

        List<CourseInfo> courses = takenCourses.stream()
                .map(this::toTakenCourseInfo)
                .sorted(Comparator.comparing(CourseInfo::getName))
                .toList();

        return GraduationCourseResponse.builder()
                .divisionCode(conditionType.name())
                .divisionName(conditionType.getDisplayName())
                .majors(List.of(MajorCourses.builder()
                        .majorType(null)
                        .departmentName(null)
                        .current(current)
                        .required(required)
                        .satisfied(satisfied)
                        .hasRequiredList(false)
                        .courses(courses)
                        .build()))
                .build();
    }

    private MajorCourses buildMajorCourses(StudentProfile profile, StudentMajor major,
            GraduationConditionType conditionType, ConditionsTab filter) {
        if (conditionType == GraduationConditionType.GRADUATION_REQUIRED) {
            return buildGraduationRequiredMajorCourses(profile, major);
        }

        GraduationAnalysisSummary summary = requireSummary(major);

        // 영어/SW: major.getDepartment() 전달 → fetchTakenCourses에서 전공 탭이면 학과 필터 적용
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
        DistributedGeAreaResult areaResult = null;
        if (conditionType == GraduationConditionType.DISTRIBUTED_GE
                && profile.getAdmissionYear() >= DISTRIBUTED_GE_AREA_YEAR_CUTOFF) {
            areaResult = computeDistributedGeAreas(takenCourses, List.of(), profile);
        }

        // GENERAL_ELECTIVE(기타)는 요구 학점 자체가 없어(required=null) 항상 satisfied=true로 계산되던
        // 걸 요청에 따라 무조건 false로 고정한다 - 졸업 요건이 아니라 참고용 집계라 "충족" 배지를 아예
        // 안 보여주기 위함.
        boolean satisfied = conditionType != GraduationConditionType.GENERAL_ELECTIVE
                && (required == null || current >= required);
        if (areaResult != null) {
            satisfied = satisfied && areaResult.satisfied();
        }

        // 미이수 후보: RequirementCourse 큐레이션 대신 Course 테이블을 직접 본다. 이 학과+이수구분에
        // 개설된 현재 활성 과목이 곧 "미이수 후보 목록"이다 - 별도 필수과목 리스트를 관리자가 손으로
        // 유지보수할 필요 없이, 커리큘럼 개정 시 Course.isActive만 갱신하면 자동으로 반영된다.
        // 졸업필수/전공필수만 미이수 표기 대상 - 전공선택/GE 등은 선택적으로 이수하는 영역이라
        // "이 과목을 안 들었다"는 표시가 의미 없다(졸업필수는 buildGraduationRequiredMajorCourses에서 별도 처리).
        List<Course> requiredCourses = (conditionType == GraduationConditionType.MAJOR_REQUIRED)
                ? toDivisionCategory(conditionType)
                        .map(category -> courseRepository.findActiveByDepartmentAndDivisionCategory(
                                major.getDepartment(), category))
                        .orElse(List.of())
                : List.of();
        boolean hasRequiredList = !requiredCourses.isEmpty();

        Set<Long> takenCourseIds = new HashSet<>();
        List<CourseInfo> courses = new ArrayList<>();

        for (StudentCourse sc : takenCourses) {
            if (sc.getCourse() != null) {
                takenCourseIds.add(sc.getCourse().getId());
            }
            courses.add(toTakenCourseInfo(sc, extractAreaInfo(sc, conditionType)));
        }

        for (Course course : requiredCourses) {
            if (!takenCourseIds.contains(course.getId())) {
                courses.add(toNotTakenCourseInfo(course));
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
                .distAreaDescriptions(areaResult != null ? buildDistAreaDescriptions(areaResult) : List.of())
                .courses(courses)
                .build();
    }

    // 학과 자체의 독립 졸업요건(division 기반 아님, 예: 스포츠의학과 졸업필수) 드릴다운 응답.
    // 하위조건이 여러 개일 수 있어 current/required는 "만족한 조건 수/전체 조건 수"로 요약하고,
    // 조건별 상세는 unmetDescriptions에 문구로 담는다. 과목 리스트는 모든 하위조건에 연결된
    // 과목을 하나로 합쳐서 보여준다(전문실기1~6 + 맨손체조가 한 리스트에 섞여 나옴).
    private MajorCourses buildGraduationRequiredMajorCourses(StudentProfile profile, StudentMajor major) {
        // 이 드릴다운 엔드포인트는 PLANNED를 지원하지 않아(별도 스코프) 항상 COMPLETED만 반영한다.
        GraduationRequiredJudgement judgement = judgeGraduationRequired(profile, major.getDepartment(), List.of());
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
                .current(judgement.satisfiedRequirementCount())
                .required(judgement.totalRequirementCount())
                .satisfied(judgement.satisfied())
                .hasRequiredList(hasRequiredList)
                .unmetDescriptions(judgement.unmetDescriptions())
                .courses(courses)
                .build();
    }

    // judgeGraduationRequired 결과를 홈 화면 요약 DTO로 변환한다. judgement가 없는 탭(GE/OTHERS처럼
    // 학과 자체가 없는 탭)이거나, 해당 학과에 이 요건 자체가 없으면(대부분의 학과) null을 반환한다 —
    // FE는 이 필드가 null인지 아닌지로 "졸업 필수" 탭/카드를 보여줄지 판단한다(스포츠의학과 등 일부만
    // non-null로 채워짐).
    // "요건이 있는지"는 items().isEmpty()가 아니라 totalRequirementCount()로 판단해야 한다 —
    // RequirementCourse는 있는데 RequirementCourseItem을 깜빡하고 안 넣은 경우, items()는 비어있지만
    // 요건 자체는 존재하는 거라 미충족(satisfied=false)으로 정확히 보여줘야지 숨기면 안 된다.
    private GraduationRequiredSummary toGraduationRequiredSummary(GraduationRequiredJudgement judgement) {
        if (judgement == null || judgement.totalRequirementCount() == 0) {
            return null;
        }
        List<RequirementProgress> items = judgement.itemProgress().stream()
                .map(p -> RequirementProgress.builder()
                        .name(p.name())
                        .current(p.current())
                        .required(p.required())
                        .unit(p.byCredit() ? "CREDITS" : "COURSES")
                        .satisfied(p.satisfied())
                        .build())
                .toList();
        return GraduationRequiredSummary.builder()
                .hasGraduationRequired(judgement.totalRequirementCount() > 0)
                .satisfied(judgement.satisfied())
                .totalCredit(judgement.totalCredit())
                .unmetDescriptions(judgement.unmetDescriptions())
                .items(items)
                .build();
    }

    // 학과의 독립 졸업요건(division=null인 RequirementCourse들)을 학생 이수내역과 대조해 판정한다.
    // 해당 학과에 이런 요건이 없으면(대부분의 학과) 항상 satisfied=true, 빈 리스트를 반환한다.
    // plannedItems: source=PLANNED일 때 getGraduation()이 이미 완료/수강중 과목을 제외해 넘겨주는
    // 신규 계획 과목 목록. 드릴다운(buildGraduationRequiredMajorCourses)처럼 PLANNED를 지원하지
    // 않는 호출부는 List.of()를 넘긴다.
    private GraduationRequiredJudgement judgeGraduationRequired(
            StudentProfile profile, Department department, List<PlannerVersionItem> plannedItems) {
        List<RequirementCourse> requirementCourses = requirementCourseRepository
                .findGraduationRequiredByDepartment(department, profile.getAdmissionYear());
        if (requirementCourses.isEmpty()) {
            return new GraduationRequiredJudgement(true, List.of(), 0, 0, 0, List.of(), List.of(), List.of());
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

        // PLANNED: 이 졸업요건 대상 과목만 걸러서 미이수 판정에 더한다. completedCreditByCourseId에
        // 이미 있는 과목은 계획에도 잡혀있을 수 없다(getGraduation()에서 이미 걸러서 넘어옴).
        List<PlannerVersionItem> plannedInScope = plannedItems.stream()
                .filter(i -> requirementCourseIds.contains(i.getCourse().getId()))
                .toList();
        Map<Long, Integer> plannedCreditByCourseId = plannedInScope.stream()
                .collect(Collectors.toMap(i -> i.getCourse().getId(), PlannerVersionItem::getCredit, (a, b) -> a));

        // minCredit이 있으면 학점 합으로, minCount가 있으면 이수 과목 수로 판정한다. 둘 다 설정된
        // row는 아래 루프에서 바로 예외를 던지므로 여기까지 오면 정확히 하나만 설정된 상태다.
        // 안내 문구(unmetDescriptions)는 학점 기준 조건만 담는다. 과목수 기준 조건(예: 맨손체조)은
        // 어차피 과목 자체가 이수/미이수 카드로 리스트에 나오기 때문에 문구로 중복해서 보여줄 필요가 없다.
        // 하위 요건별 수치는 itemProgress(→ RequirementProgress)가 대신 담당한다.
        boolean satisfied = true;
        int satisfiedCount = 0;
        List<String> unmetDescriptions = new ArrayList<>();
        List<RequirementItemProgress> itemProgress = new ArrayList<>();
        for (RequirementCourse rc : requirementCourses) {
            // minCredit/minCount는 시드 데이터로 직접 들어가서(Java 빌더를 안 거침) 엔티티 레벨 검증으로는
            // 못 막는다. 둘 다 설정된 row가 들어오면 어느 쪽이 무시됐는지 모른 채 조용히 잘못 판정하는
            // 대신, 여기서 바로 예외를 던져서 데이터 실수를 즉시 드러낸다.
            if (rc.getMinCredit() > 0 && rc.getMinCount() > 0) {
                throw new IllegalStateException(
                        "RequirementCourse(id=" + rc.getId() + ")에 minCredit/minCount가 둘 다 설정돼 있습니다. "
                                + "하나만 설정해야 합니다.");
            }
            List<RequirementCourseItem> items = itemsByRequirement.getOrDefault(rc.getId(), List.of());
            boolean byCredit = rc.getMinCredit() > 0;
            int current = byCredit
                    ? items.stream()
                            .mapToInt(item -> {
                                Long courseId = item.getCourse().getId();
                                Integer completed = completedCreditByCourseId.get(courseId);
                                return completed != null ? completed : plannedCreditByCourseId.getOrDefault(courseId, 0);
                            })
                            .sum()
                    : (int) items.stream()
                            .filter(item -> {
                                Long courseId = item.getCourse().getId();
                                return completedCreditByCourseId.containsKey(courseId)
                                        || plannedCreditByCourseId.containsKey(courseId);
                            })
                            .count();
            int required = byCredit ? rc.getMinCredit() : rc.getMinCount();
            boolean rowSatisfied = current >= required;
            if (!rowSatisfied) {
                satisfied = false;
                if (byCredit) {
                    unmetDescriptions.add("[" + rc.getName() + "] " + current + "/" + required + "학점 이수완료");
                }
            } else {
                satisfiedCount++;
            }
            itemProgress.add(new RequirementItemProgress(rc.getName(), current, required, byCredit, rowSatisfied));
        }

        int totalCredit = takenCourses.stream().mapToInt(StudentCourse::getCredit).sum()
                + plannedInScope.stream().mapToInt(PlannerVersionItem::getCredit).sum();
        return new GraduationRequiredJudgement(satisfied, unmetDescriptions, totalCredit,
                satisfiedCount, requirementCourses.size(), allItems, takenCourses, itemProgress);
    }

    // satisfiedRequirementCount/totalRequirementCount는 학점/과목수 조건 전부(unmetDescriptions에
    // 안 담기는 과목수 조건 포함)를 센 값이라, current==required면 항상 satisfied=true와 일치한다.
    private record GraduationRequiredJudgement(
            boolean satisfied,
            List<String> unmetDescriptions,
            int totalCredit,
            int satisfiedRequirementCount,
            int totalRequirementCount,
            List<RequirementCourseItem> items,
            List<StudentCourse> takenCourses,
            List<RequirementItemProgress> itemProgress
    ) {
    }

    // RequirementCourse 한 행(예: 전문실기, 맨손체조)의 진행 현황. toGraduationRequiredSummary에서
    // RequirementProgress DTO로 변환된다.
    private record RequirementItemProgress(
            String name, int current, int required, boolean byCredit, boolean satisfied
    ) {
    }

    // 영어/SW: 전공 탭(MAJOR)은 이수구분 + 개설학과 기준 필터링으로 그 전공 하나만 구분
    //          교양/기타 탭은 이수구분 기준만 적용
    // majorType=ALL(탭·학과 구분 없는 전체 합산)은 이 메서드에 도달하지 않는다
    // (getCoursesByDivision에서 buildMergedFlagCourseResponse로 먼저 처리됨).
    private List<StudentCourse> fetchTakenCourses(StudentProfile profile,
            GraduationConditionType conditionType, ConditionsTab filter, Department department) {
        if (conditionType == GraduationConditionType.ENGLISH_COURSE) {
            List<DivisionCategory> cats = getDivisionCategoriesForTab(filter);
            if (filter == ConditionsTab.MAJOR) {
                return studentCourseRepository
                        .findByStudentProfileAndCourseIsEnglishAndDivisionCategoryInAndDepartment(
                                profile, cats, department);
            }
            return studentCourseRepository
                    .findByStudentProfileAndCourseIsEnglishAndDivisionCategoryIn(profile, cats);
        }
        if (conditionType == GraduationConditionType.SW_CERT_COURSE) {
            List<DivisionCategory> cats = getDivisionCategoriesForTab(filter);
            if (filter == ConditionsTab.MAJOR) {
                return studentCourseRepository
                        .findByStudentProfileAndCourseIsSwAndDivisionCategoryInAndDepartment(
                                profile, cats, department);
            }
            return studentCourseRepository
                    .findByStudentProfileAndCourseIsSwAndDivisionCategoryIn(profile, cats);
        }
        if (conditionType == GraduationConditionType.DISTRIBUTED_GE) {
            return fetchDistributedGeCourses(profile);
        }
        return toDivisionCategory(conditionType)
                .flatMap(cat -> divisionRepository.findBySchoolAndCategory(profile.getSchool(), cat))
                .map(div -> studentCourseRepository.findByStudentProfileAndAppliedDivisionIn(profile, List.of(div)))
                .orElse(List.of());
    }

    private List<StudentCourse> fetchDistributedGeCourses(StudentProfile profile) {
        return divisionRepository.findBySchoolAndCategory(profile.getSchool(), DivisionCategory.DISTRIBUTED_GE)
                .map(div -> studentCourseRepository.findByStudentProfileAndAppliedDivisionWithGeArea(profile, div))
                .orElse(List.of());
    }

    private DistributedGeAreaResult computeDistributedGeAreas(
            List<StudentCourse> distributedGeCourses,
            List<PlannerVersionItem> plannedItems,
            StudentProfile profile
    ) {
        Set<String> coveredCodes = distributedGeCourses.stream()
                .filter(sc -> sc.getCourse() != null && sc.getCourse().getGeArea() != null)
                .map(sc -> sc.getCourse().getGeArea().getCode())
                .collect(Collectors.toSet());
        for (PlannerVersionItem item : plannedItems) {
            if (item.getPlannedDivision() != null
                    && item.getPlannedDivision().getCategory() == DivisionCategory.DISTRIBUTED_GE
                    && item.getCourse().getGeArea() != null) {
                coveredCodes.add(item.getCourse().getGeArea().getCode());
            }
        }
        Map<String, String> areaNameMap = geAreaRepository.findBySchool(profile.getSchool()).stream()
                .collect(Collectors.toMap(GeArea::getCode, GeArea::getName));
        List<AreaStatusInfo> areas = DISTRIBUTED_GE_AREA_CODES.stream()
                .map(code -> new AreaStatusInfo(code, areaNameMap.getOrDefault(code, code), coveredCodes.contains(code)))
                .toList();
        int completedCount = (int) areas.stream().filter(AreaStatusInfo::completed).count();
        return new DistributedGeAreaResult(
                completedCount, DISTRIBUTED_GE_REQUIRED_AREA_COUNT,
                completedCount >= DISTRIBUTED_GE_REQUIRED_AREA_COUNT, areas);
    }

    private List<String> buildDistAreaDescriptions(DistributedGeAreaResult result) {
        String text = result.areas().stream()
                .filter(AreaStatusInfo::completed)
                .map(a -> "[" + a.name() + "]영역")
                .collect(Collectors.joining(", "));
        return text.isEmpty() ? List.of() : List.of(text + " 이수 완료");
    }

    private record AreaStatusInfo(String code, String name, boolean completed) {}

    private record DistributedGeAreaResult(
            int completedCount, int requiredCount, boolean satisfied, List<AreaStatusInfo> areas
    ) {}

    private Optional<DivisionCategory> toDivisionCategory(GraduationConditionType conditionType) {
        try {
            return Optional.of(DivisionCategory.valueOf(conditionType.name()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private CourseInfo toTakenCourseInfo(StudentCourse sc) {
        return toTakenCourseInfo(sc, null);
    }

    private CourseInfo toTakenCourseInfo(StudentCourse sc, AreaInfo area) {
        String departmentName = sc.getCourse() != null && sc.getCourse().getOfferingDepartment() != null
                ? sc.getCourse().getOfferingDepartment().getName() : null;
        return CourseInfo.builder()
                .studentCourseId(sc.getId())
                .name(sc.getRawCourseName())
                .departmentName(departmentName)
                .credit(sc.getCredit())
                .semester(sc.getTakenSemester() != null ? semesterName(sc.getTakenSemester()) : null)
                .taken(true)
                .isEnglish(sc.getCourse() != null && sc.getCourse().isEnglish())
                .isSw(sc.getCourse() != null && sc.getCourse().isSw())
                .area(area)
                .build();
    }

    private AreaInfo extractAreaInfo(StudentCourse sc, GraduationConditionType conditionType) {
        if (conditionType != GraduationConditionType.DISTRIBUTED_GE
                || sc.getCourse() == null || sc.getCourse().getGeArea() == null) return null;
        GeArea geArea = sc.getCourse().getGeArea();
        return new AreaInfo(geArea.getCode(), geArea.getName());
    }

    private CourseInfo toNotTakenCourseInfo(RequirementCourseItem item) {
        return toNotTakenCourseInfo(item.getCourse());
    }

    private CourseInfo toNotTakenCourseInfo(Course course) {
        String departmentName = course.getOfferingDepartment() != null
                ? course.getOfferingDepartment().getName() : null;
        return CourseInfo.builder()
                .studentCourseId(null)
                .name(course.getName())
                .departmentName(departmentName)
                .credit(course.getCredit())
                .semester(openedSemesterName(course.getOpenedSemester()))
                .taken(false)
                .isEnglish(course.isEnglish())
                .isSw(course.isSw())
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

    // 졸업 가능 여부: 학점 요건 + 비학점 요건(평점, 논문/졸업능력인정 등 인증) 전부 충족 시 true
    // 전공 요건(학점 + 졸업필수)은 보유 전공 전부 독립적으로 확인, 교양/영어/SW는 공통 기준(본전공)
    private boolean computeGraduatable(
            List<StudentMajorContext> majorContexts,
            List<CertResult> allCerts,
            DistributedGeAreaResult geAreaResult
    ) {
        // 전공별 학점 요건 + 졸업필수 요건
        for (StudentMajorContext ctx : majorContexts) {
            if (!isMajorRequirementMet(ctx.effectiveSummary())) return false;
            if (!ctx.judgement().satisfied()) return false;
        }

        GraduationAnalysisSummary mainSummary = mainContext(majorContexts).effectiveSummary();

        if (mainSummary.getRequiredGeCurrent() < mainSummary.getRequiredGeRequired()) return false;
        if (mainSummary.getDistributedGeCurrent() < mainSummary.getDistributedGeRequired()) return false;
        if (geAreaResult != null && !geAreaResult.satisfied()) return false;
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