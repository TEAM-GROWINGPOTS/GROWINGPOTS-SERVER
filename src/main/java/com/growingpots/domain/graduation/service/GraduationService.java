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
import com.growingpots.domain.transcript.entity.enums.CertType;
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
import java.util.LinkedHashMap;
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

        // PLANNED: 플래너 선택 버전의 과목 중 아직 이수/수강 중이지 않은 과목만 delta로 반영한다.
        // original.totalCreditCurrent는 PDF 스냅샷으로 COMPLETED + IN_PROGRESS 학점을 이미 포함하므로
        // 해당 courseId를 delta에서 제외하지 않으면 중복 합산된다.
        // 플래너가 없으면 COMPLETED와 동일한 응답.
        List<PlannerVersionItem> allPlannedItems = List.of();
        if (source == GraduationSource.PLANNED) {
            List<PlannerVersionItem> raw = plannerVersionItemRepository.findSelectedByStudentProfile(profile);
            Set<Long> alreadyCounted = new HashSet<>(
                    studentCourseRepository.findCourseIdsByStudentProfileAndStatusIn(
                            profile, List.of(CourseStatus.COMPLETED, CourseStatus.IN_PROGRESS)));
            allPlannedItems = raw.stream()
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
        boolean curriculumSatisfied = computeAcademicRequirementMet(
                profile, majorContexts, geAreaResult, allPlannedItems);

        if (studentMajorId != null) {
            StudentMajorContext target = findMajorContextByStudentMajorId(majorContexts, studentMajorId);
            return buildSingleTabResponse(profile, target.effectiveSummary(), ConditionsTab.MAJOR,
                    target.judgement(), graduatable, curriculumSatisfied, target.certs(), null,
                    target.major().getMajorType() == MajorType.MAIN);
        }

        StudentMajorContext mainContext = mainContext(majorContexts);
        return switch (majorTypeFilter) {
            case GE, OTHERS -> buildSingleTabResponse(profile, mainContext.effectiveSummary(),
                    majorTypeFilter == MajorTypeFilter.GE ? ConditionsTab.GE : ConditionsTab.OTHERS,
                    null, graduatable, curriculumSatisfied, mainContext.certs(), geAreaResult, false);
            case ALL -> buildAllTabResponse(profile, majorContexts, graduatable, curriculumSatisfied, geAreaResult);
        };
    }

    // 전공 하나(본전공이든 복수전공이든 구분 없이) 또는 GE/OTHERS 탭 단건 응답
    private GraduationResponse buildSingleTabResponse(
            StudentProfile profile,
            GraduationAnalysisSummary summary,
            ConditionsTab tab,
            GraduationRequiredJudgement judgement,  // 전공 탭만 값 있음, GE/OTHERS는 null
            boolean graduatable,
            boolean curriculumSatisfied,
            List<CertResult> certs,
            DistributedGeAreaResult geAreaResult,  // GE/OTHERS 탭만 전달, 전공 탭은 null
            boolean isMainMajor  // 영어/SW 실제값 노출 여부(본전공만). GE/OTHERS 탭은 항상 false
    ) {
        return GraduationResponse.builder()
                .summary(buildSummary(profile, summary))
                .graduatable(graduatable)
                .curriculumSatisfied(curriculumSatisfied)
                .conditions(buildConditionsForTab(summary, tab, geAreaResult, isMainMajor))
                .graduationRequired(toGraduationRequiredSummary(judgement))
                .sections(null)
                .certs(buildCertInfos(certs, summary))
                .build();
    }

    // ALL 탭: 보유 전공 전부(본전공 + 복수전공 몇 개든) + 교양 + 기타 섹션 분리 응답
    private GraduationResponse buildAllTabResponse(
            StudentProfile profile,
            List<StudentMajorContext> majorContexts,
            boolean graduatable,
            boolean curriculumSatisfied,
            DistributedGeAreaResult geAreaResult
    ) {
        List<TabSection> majorSections = majorContexts.stream()
                .map(ctx -> TabSection.builder()
                        .majorName(ctx.major().getDepartment().getName())
                        .majorType(ctx.major().getMajorType().name())
                        .conditions(buildConditionsForTab(ctx.effectiveSummary(),
                                ConditionsTab.MAJOR, null,
                                ctx.major().getMajorType() == MajorType.MAIN))
                        .graduationRequired(toGraduationRequiredSummary(ctx.judgement()))
                        .build())
                .toList();

        StudentMajorContext mainContext = mainContext(majorContexts);

        TabSection geSection = TabSection.builder()
                .conditions(buildConditionsForTab(mainContext.effectiveSummary(),
                        ConditionsTab.GE, geAreaResult, false))
                .build();

        TabSection othersSection = TabSection.builder()
                .conditions(buildConditionsForTab(mainContext.effectiveSummary(),
                        ConditionsTab.OTHERS, null, false))
                .build();

        return GraduationResponse.builder()
                .summary(buildSummary(profile, mainContext.effectiveSummary()))
                .graduatable(graduatable)
                .curriculumSatisfied(curriculumSatisfied)
                .conditions(null)
                .sections(AllSections.builder()
                        .majors(majorSections)
                        .ge(geSection)
                        .others(othersSection)
                        .build())
                .certs(buildCertInfos(mainContext.certs(), mainContext.effectiveSummary()))
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
    // - 교양 탭(GE): REQUIRED_GE/DISTRIBUTED_GE/FREE_GE + SW(교양 이수구분). 영어는 전공 탭에만 표시.
    // - 기타 탭(OTHERS): GENERAL_ELECTIVE (영어·SW 미포함)
    private List<ConditionInfo> buildConditionsForTab(
            GraduationAnalysisSummary summary,
            ConditionsTab tab,
            DistributedGeAreaResult geAreaResult,
            boolean isMainMajor
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

        // 영어/SW: 기획 확정 - '전체'(본전공) 탭에서만 실제 값을 보여준다. 항목 자체는 다른 탭(복수전공/
        // 교양/기타)에도 구조 유지를 위해 계속 내려주되, isMainMajor가 아니면 current를 0으로 고정한다.
        // (예전엔 탭의 이수구분 카테고리로 StudentCourse를 매번 다시 긁어 재계산했는데, 실제 SW인증 과목이
        // 자유이수(교양) 쪽에 몰려있는 경우가 많아 전공 탭에서 0으로 보이는 버그가 있었다 - PDF 스냅샷
        // summary.englishCurrent/swCertCurrent는 학생 전체 기준값이라 탭별 재계산 자체가 불필요했다.)
        if (tab != ConditionsTab.OTHERS) {
            if (tab == ConditionsTab.MAJOR) {
                result.add(buildEnglishConditionInfo(summary, isMainMajor));
            }
            result.add(buildSwConditionInfo(summary, isMainMajor));
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

    // 영어강의 이수 수 조건 정보 생성. current는 본전공 탭에서만 PDF 스냅샷(summary.englishCurrent, 학생
    // 전체 기준값)을 쓰고, 그 외(복수전공/교양/기타) 탭은 0으로 고정한다 - 영어/SW는 '전체'(본전공) 탭에서만
    // 노출하기로 기획 확정. summary는 호출부에서 이미 PLANNED 모드 델타(buildAdjustedSummary)까지 반영된
    // effectiveSummary라 여기서 plannedItems를 또 더하면 이중 계산이 된다 - 그대로 읽기만 한다.
    private ConditionInfo buildEnglishConditionInfo(GraduationAnalysisSummary summary, boolean isMainMajor) {
        int current = isMainMajor ? summary.getEnglishCurrent() : 0;
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

    // SW인증강의 학점 합계 조건 정보 생성. current는 본전공 탭에서만 PDF 스냅샷(summary.swCertCurrent, 학생
    // 전체 기준값)을 쓰고, 그 외(복수전공/교양/기타) 탭은 0으로 고정한다 - 영어/SW는 '전체'(본전공) 탭에서만
    // 노출하기로 기획 확정. summary는 호출부에서 이미 PLANNED 모드 델타(buildAdjustedSummary)까지 반영된
    // effectiveSummary라 여기서 plannedItems를 또 더하면 이중 계산이 된다 - 그대로 읽기만 한다.
    private ConditionInfo buildSwConditionInfo(GraduationAnalysisSummary summary, boolean isMainMajor) {
        int current = 0;
        if (isMainMajor) {
            current = summary.getSwCertCurrent() != null ? summary.getSwCertCurrent() : 0;
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

    // PLANNED 모드용: 스냅샷 기반 summary에 신규 계획 과목의 학점 delta를 더해 새 in-memory summary를 반환한다.
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
                .enrollmentStatus(profile.getEnrollmentStatus() != null ? profile.getEnrollmentStatus() + " 중" : null)
                .build();
    }

    private List<CertInfo> buildCertInfos(List<CertResult> certs, GraduationAnalysisSummary summary) {
        List<CertInfo> result = new ArrayList<>();
        result.add(buildGpaCertInfo(summary));
        certs.stream()
                .filter(c -> c.getCertType() != CertType.ENGLISH && c.getCertType() != CertType.SW)
                .map(c -> new CertInfo(c.getCertType().name(), c.getResult().name()))
                .forEach(result::add);
        boolean hasGraduationCert = certs.stream()
                .anyMatch(c -> c.getCertType() == CertType.GRADUATION_CERT);
        if (!hasGraduationCert) {
            result.add(new CertInfo(CertType.GRADUATION_CERT.name(), CertJudgement.NONE.name()));
        }
        return result;
    }

    private CertInfo buildGpaCertInfo(GraduationAnalysisSummary summary) {
        String judged;
        if (summary.getGpaRequired() == null) {
            judged = CertJudgement.NONE.name();
        } else if (summary.getGpaCurrent() == null) {
            judged = CertJudgement.NONE.name();
        } else if (summary.getGpaCurrent().compareTo(summary.getGpaRequired()) < 0) {
            judged = CertJudgement.FAIL.name();
        } else {
            judged = CertJudgement.PASS.name();
        }
        return new CertInfo("GPA", judged);
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
                        .conditionCode(conditionType.name())
                        .conditionName(conditionType.getDisplayName())
                        .majors(List.of())
                        .build();
            }
            if (majorTypeFilter == MajorTypeFilter.ALL) {
                return buildMergedFlagCourseResponse(profile, majors, conditionType);
            }
        }

        List<StudentMajor> targetMajors = studentMajorId != null
                ? List.of(findMajorByStudentMajorId(majors, studentMajorId))
                : switch (majorTypeFilter) {
                    case ALL -> majors;
                    case GE, OTHERS -> majors.stream().filter(m -> m.getMajorType() == MajorType.MAIN).toList();
                };

        List<MajorCourses> majorCoursesList = targetMajors.stream()
                .map(major -> buildMajorCourses(profile, major, conditionType))
                .toList();

        return GraduationCourseResponse.builder()
                .conditionCode(conditionType.name())
                .conditionName(conditionType.getDisplayName())
                .majors(majorCoursesList)
                .build();
    }

    private StudentMajor findMajorByStudentMajorId(List<StudentMajor> majors, Long studentMajorId) {
        return majors.stream()
                .filter(m -> m.getId().equals(studentMajorId))
                .findFirst()
                .orElseThrow(() -> new BaseException(ErrorCode.STUDENT_MAJOR_NOT_FOUND));
    }

    // 같은 과목을 재수강하면 완료(COMPLETED) 행과 진행중(IN_PROGRESS) 행이 같이 나올 수 있는데, 영어/SW
    // 목록에서는 같은 과목을 두 번 보여주면 안 되므로 course 하나당 한 행만 남긴다. 완료 이력이 있으면
    // 그 행을 우선하고(이미 이수했다는 사실은 확정적), 없으면 진행중 행을 보여준다.
    private List<StudentCourse> dedupeByCourse(List<StudentCourse> courses) {
        Map<Long, StudentCourse> byCourseId = new LinkedHashMap<>();
        for (StudentCourse sc : courses) {
            if (sc.getCourse() == null) {
                continue;
            }
            Long courseId = sc.getCourse().getId();
            StudentCourse existing = byCourseId.get(courseId);
            if (existing == null
                    || (existing.getStatus() != CourseStatus.COMPLETED && sc.getStatus() == CourseStatus.COMPLETED)) {
                byCourseId.put(courseId, sc);
            }
        }
        return new ArrayList<>(byCourseId.values());
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

        // current는 PDF 스냅샷(summary.englishCurrent/swCertCurrent, 학생 전체 기준값)을 그대로 쓴다 -
        // 예전엔 takenCourses.size()/sum()으로 실시간 재계산했는데, 그 쿼리에 status 필터가 없어서
        // 진행중(재수강 중)인 과목까지 세는 바람에 스냅샷 값과 어긋났다(#188 연장선에서 발견).
        List<StudentCourse> takenCourses;
        int current;
        Integer required;
        if (conditionType == GraduationConditionType.ENGLISH_COURSE) {
            takenCourses = dedupeByCourse(studentCourseRepository.findByStudentProfileAndCourseIsEnglish(profile));
            current = summary.getEnglishCurrent();
            required = summary.getEnglishRequired();
        } else {
            takenCourses = dedupeByCourse(studentCourseRepository.findByStudentProfileAndCourseIsSw(profile));
            current = summary.getSwCertCurrent() != null ? summary.getSwCertCurrent() : 0;
            required = summary.getSwCertRequired();
        }
        boolean satisfied = required == null || current >= required;

        List<CourseInfo> courses = takenCourses.stream()
                .map(this::toTakenCourseInfo)
                .sorted(Comparator.comparing(CourseInfo::getName))
                .toList();

        return GraduationCourseResponse.builder()
                .conditionCode(conditionType.name())
                .conditionName(conditionType.getDisplayName())
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
            GraduationConditionType conditionType) {
        if (conditionType == GraduationConditionType.GRADUATION_REQUIRED) {
            return buildGraduationRequiredMajorCourses(profile, major);
        }

        GraduationAnalysisSummary summary = requireSummary(major);
        // 영어/SW는 '전체'(본전공) 탭에서만 실제 값을 노출하기로 확정했다(GraduationService의 조건 요약과
        // 동일한 정책) - 복수전공은 목록/current 둘 다 비워서 요약 카드(0)와 모순되지 않게 한다.
        boolean isMainMajor = major.getMajorType() == MajorType.MAIN;
        boolean isEnglishOrSw = conditionType == GraduationConditionType.ENGLISH_COURSE
                || conditionType == GraduationConditionType.SW_CERT_COURSE;

        // 영어/SW는 이수구분·학과와 무관하게 학생 전체 기준 개념(summary.englishCurrent 등도 마찬가지)이라,
        // 본전공 탭이어도 전공 이수구분(MAJOR_CATEGORIES)으로 목록을 제한하면 안 된다 - 실제로 SW인증
        // 과목이 자유이수(교양)에 있는 경우가 흔해서, 제한하면 목록이 스냅샷과 어긋나거나 아예 비어버린다.
        // 그 외 이수구분(MAJOR_BASIC 등)은 기존대로 fetchTakenCourses의 카테고리/학과 필터를 그대로 쓴다.
        List<StudentCourse> takenCourses;
        if (isEnglishOrSw) {
            takenCourses = isMainMajor
                    ? dedupeByCourse(conditionType == GraduationConditionType.ENGLISH_COURSE
                            ? studentCourseRepository.findByStudentProfileAndCourseIsEnglish(profile)
                            : studentCourseRepository.findByStudentProfileAndCourseIsSw(profile))
                    : List.of();
        } else {
            // 재수강 중인 과목은 과거 완료 이력(COMPLETED)과 현재 진행중 이력(IN_PROGRESS)이 별도
            // StudentCourse 행으로 둘 다 남아있어(재수강 이력 보존은 의도된 동작), dedup 안 하면 "이수 과목"
            // 카드에 같은 과목이 두 번 뜬다(#198, 영어/SW에서 먼저 발견해 고친 것과 동일한 근본 원인).
            takenCourses = dedupeByCourse(fetchTakenCourses(profile, conditionType));
        }

        // 영어/SW의 current는 PDF 스냅샷(summary.englishCurrent/swCertCurrent, 학생 전체 기준값)을 그대로
        // 쓴다 - 예전엔 takenCourses.size()/sum()으로 실시간 재계산했는데, status 필터가 없어 진행중 과목까지
        // 세는 바람에 스냅샷 값과 어긋났다(#188 연장선에서 발견). 나머지 이수구분은 그대로 스냅샷 값 사용.
        int current;
        Integer required;
        if (conditionType == GraduationConditionType.ENGLISH_COURSE) {
            current = isMainMajor ? summary.getEnglishCurrent() : 0;
            required = summary.getEnglishRequired();
        } else if (conditionType == GraduationConditionType.SW_CERT_COURSE) {
            current = isMainMajor ? (summary.getSwCertCurrent() != null ? summary.getSwCertCurrent() : 0) : 0;
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
                courses.add(toNotTakenCourseInfo(course, course.getDefaultDivision()));
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
                courses.add(toNotTakenCourseInfo(item, item.getCourse().getDefaultDivision()));
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

        // PLANNED: 이 졸업요건 대상 과목만 걸러서 미이수 판정에 더한다. 재수강 과목(이미 completedCreditByCourseId에
        // 있는 과목)이 plannedItems에 포함될 수 있으며, 아래 판정 로직에서 completed 값을 우선 사용하므로
        // 졸업요건 판정에서는 이중카운트가 발생하지 않는다.
        List<PlannerVersionItem> plannedInScope = plannedItems.stream()
                .filter(i -> requirementCourseIds.contains(i.getCourse().getId()))
                .toList();
        Map<Long, Integer> plannedCreditByCourseId = plannedInScope.stream()
                .collect(Collectors.toMap(i -> i.getCourse().getId(), PlannerVersionItem::getCredit, (a, b) -> a));

        // minCredit이 있으면 학점 합으로, minCount가 있으면 이수 과목 수로 판정한다. 둘 다 설정된
        // row는 아래 루프에서 바로 예외를 던지므로 여기까지 오면 정확히 하나만 설정된 상태다.
        // 안내 문구(unmetDescriptions): 학점 기준 조건은 조건별로 한 줄씩 담고, 과목수 기준 조건
        // (예: 전문실기, 맨손체조)은 buildCourseUnmetDescription으로 한 줄에 합쳐서 담는다(#227) -
        // 스포츠의학과처럼 하위 요건이 전부 과목수 기준이면 예전엔 unmetDescriptions가 항상 빈
        // 리스트라 프론트 help text가 아예 안 내려가는 버그가 있었다.
        boolean satisfied = true;
        int satisfiedCount = 0;
        List<String> unmetDescriptions = new ArrayList<>();
        List<RequirementItemProgress> itemProgress = new ArrayList<>();
        List<RequirementItemProgress> unmetCourseItems = new ArrayList<>();
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
            RequirementItemProgress progress = new RequirementItemProgress(rc.getName(), current, required, byCredit, rowSatisfied);
            if (!rowSatisfied) {
                satisfied = false;
                if (byCredit) {
                    unmetDescriptions.add("[" + rc.getName() + "] " + current + "/" + required + "학점 이수완료");
                } else {
                    unmetCourseItems.add(progress);
                }
            } else {
                satisfiedCount++;
            }
            itemProgress.add(progress);
        }
        String courseUnmetDescription = buildCourseUnmetDescription(unmetCourseItems);
        if (courseUnmetDescription != null) {
            unmetDescriptions.add(courseUnmetDescription);
        }

        int totalCredit = takenCourses.stream().mapToInt(StudentCourse::getCredit).sum()
                + plannedInScope.stream().mapToInt(PlannerVersionItem::getCredit).sum();
        return new GraduationRequiredJudgement(satisfied, unmetDescriptions, totalCredit,
                satisfiedCount, requirementCourses.size(), allItems, takenCourses, itemProgress);
    }

    // 과목수 기준 하위요건(예: 전문실기, 맨손체조)의 미충족 현황을 한 줄로 합친다(#227).
    // 첫 항목만 "[졸업필수(이름 요구과목수과목)] 상태" 형태로 라벨을 달고, 나머지는 "이름 상태"로
    // 콤마 연결한다. 상태는 0과목 이수했으면 "미이수", 아니면 "현재/요구과목 이수 완료".
    // 예: "[졸업필수(전문실기 2과목)] 1/2과목 이수 완료, 맨손체조 미이수"
    private String buildCourseUnmetDescription(List<RequirementItemProgress> unmetCourseItems) {
        if (unmetCourseItems.isEmpty()) {
            return null;
        }
        StringBuilder description = new StringBuilder();
        for (int i = 0; i < unmetCourseItems.size(); i++) {
            RequirementItemProgress item = unmetCourseItems.get(i);
            String status = item.current() == 0
                    ? "미이수"
                    : item.current() + "/" + item.required() + "과목 이수 완료";
            if (i == 0) {
                description.append("[졸업필수(").append(item.name()).append(' ').append(item.required())
                        .append("과목)] ").append(status);
            } else {
                description.append(", ").append(item.name()).append(' ').append(status);
            }
        }
        return description.toString();
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

    // 영어/SW는 buildMajorCourses에서 이 메서드를 안 거치고 학생 전체 기준으로 직접 조회한다(이수구분·
    // 학과로 제한하면 안 되는 개념이라서 - dedupeByCourse 호출부 참고). 그 외 이수구분만 여기서 처리한다.
    // majorType=ALL(탭·학과 구분 없는 전체 합산)은 이 메서드에 도달하지 않는다
    // (getCoursesByDivision에서 buildMergedFlagCourseResponse로 먼저 처리됨).
    private List<StudentCourse> fetchTakenCourses(StudentProfile profile, GraduationConditionType conditionType) {
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
        Division div = sc.getAppliedDivision();
        return CourseInfo.builder()
                .studentCourseId(sc.getId())
                .name(sc.getRawCourseName())
                .divisionCode(div != null ? div.getCategory().name() : null)
                .divisionName(div != null ? div.getCategory().getDisplayName() : null)
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

    private CourseInfo toNotTakenCourseInfo(RequirementCourseItem item, Division division) {
        return toNotTakenCourseInfo(item.getCourse(), division);
    }

    private CourseInfo toNotTakenCourseInfo(Course course, Division division) {
        String departmentName = course.getOfferingDepartment() != null
                ? course.getOfferingDepartment().getName() : null;
        return CourseInfo.builder()
                .studentCourseId(null)
                .name(course.getName())
                .divisionCode(division != null ? division.getCategory().name() : null)
                .divisionName(division != null ? division.getCategory().getDisplayName() : null)
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

        if (mainSummary.getTotalCreditCurrent() < mainSummary.getTotalCreditRequired()) return false;

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

    // 학점·과목 이수 기반 졸업요건 충족 여부. GPA·비학점 인증 요건을 제외하고, 전공필수·전공기초·
    // 필수교과 개별 과목 이수까지 포함해 확인한다. graduatable과 달리 평점·인증 외 요소만으로 "사실상
    // 이수 완료"인지 빠르게 판단할 때 사용한다.
    // - 전공 요건(학점 + 졸업필수 + 개별 과목)은 보유 전공 전부 독립적으로 확인
    // - 교양·영어·SW는 공통 기준(본전공)
    private boolean computeAcademicRequirementMet(
            StudentProfile profile,
            List<StudentMajorContext> majorContexts,
            DistributedGeAreaResult geAreaResult,
            List<PlannerVersionItem> allPlannedItems
    ) {
        // 이수 완료 과목 ID + 계획 과목 ID를 하나의 집합으로 관리 (개별 과목 이수 판정용)
        Set<Long> takenIds = new HashSet<>(studentCourseRepository
                .findCourseIdsByStudentProfileAndStatusIn(profile, List.of(CourseStatus.COMPLETED)));
        allPlannedItems.forEach(i -> takenIds.add(i.getCourse().getId()));

        for (StudentMajorContext ctx : majorContexts) {
            // 카테고리별 학점 기준 충족 (전공기초/전공필수/전공선택 각각 독립 확인)
            if (!isMajorRequirementMet(ctx.effectiveSummary())) return false;
            // 학과 독립 졸업필수 요건 (스포츠의학과 등 해당 학과에만 존재)
            if (!ctx.judgement().satisfied()) return false;

            Department dept = ctx.major().getDepartment();
            // 전공필수 개별 과목 모두 이수 여부
            List<Course> majorRequired = courseRepository.findActiveByDepartmentAndDivisionCategory(
                    dept, DivisionCategory.MAJOR_REQUIRED);
            if (majorRequired.stream().anyMatch(c -> !takenIds.contains(c.getId()))) return false;
            // 전공기초 개별 과목 모두 이수 여부
            List<Course> majorBasic = courseRepository.findActiveByDepartmentAndDivisionCategory(
                    dept, DivisionCategory.MAJOR_BASIC);
            if (majorBasic.stream().anyMatch(c -> !takenIds.contains(c.getId()))) return false;
        }

        GraduationAnalysisSummary mainSummary = mainContext(majorContexts).effectiveSummary();

        if (mainSummary.getTotalCreditCurrent() < mainSummary.getTotalCreditRequired()) return false;

        // 교양 학점 기준 (필수교과/배분이수/자유이수 각각 독립 확인)
        if (mainSummary.getRequiredGeCurrent() < mainSummary.getRequiredGeRequired()) return false;
        if (mainSummary.getDistributedGeCurrent() < mainSummary.getDistributedGeRequired()) return false;
        if (geAreaResult != null && !geAreaResult.satisfied()) return false;
        if (mainSummary.getFreeGeCurrent() < mainSummary.getFreeGeRequired()) return false;

        // 필수교과 개별 과목 모두 이수 여부 (학교 공통 교양 - Division 기준 조회)
        Optional<Division> requiredGeDivision = divisionRepository.findBySchoolAndCategory(
                profile.getSchool(), DivisionCategory.REQUIRED_GE);
        if (requiredGeDivision.isPresent()) {
            List<Course> requiredGeCourses = courseRepository.findActiveByDefaultDivision(requiredGeDivision.get());
            if (requiredGeCourses.stream().anyMatch(c -> !takenIds.contains(c.getId()))) return false;
        }

        // 영어 강의 수 기준 (과목 수, 비학점 인증 아님)
        if (mainSummary.getEnglishCurrent() < mainSummary.getEnglishRequired()) return false;

        // SW 인증 강의 수 기준 (과목 수, 비학점 인증 아님)
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