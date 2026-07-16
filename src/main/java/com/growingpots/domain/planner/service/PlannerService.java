package com.growingpots.domain.planner.service;

import com.growingpots.domain.planner.dto.request.PlannerSaveRequest;
import com.growingpots.domain.planner.dto.request.PrerequisiteCheckRequest;
import com.growingpots.domain.planner.dto.request.SelectVersionRequest;
import com.growingpots.domain.planner.dto.response.PlannerResponse;
import com.growingpots.domain.planner.dto.response.PrerequisiteCheckResponse;
import com.growingpots.domain.planner.dto.response.RetakeDisplay;
import com.growingpots.domain.planner.dto.response.SelectVersionResponse;
import com.growingpots.domain.planner.entity.PlannerSimulation;
import com.growingpots.domain.planner.entity.PlannerTerm;
import com.growingpots.domain.planner.entity.PlannerTermVersion;
import com.growingpots.domain.planner.entity.PlannerVersionItem;
import com.growingpots.domain.planner.repository.PlannerSimulationRepository;
import com.growingpots.domain.planner.repository.PlannerTermRepository;
import com.growingpots.domain.planner.repository.PlannerTermVersionRepository;
import com.growingpots.domain.planner.repository.PlannerVersionItemRepository;
import com.growingpots.domain.transcript.entity.StudentCourse;
import com.growingpots.domain.transcript.entity.enums.CourseStatus;
import com.growingpots.domain.transcript.entity.enums.Semester;
import com.growingpots.domain.transcript.repository.StudentCourseRepository;
import com.growingpots.domain.university.entity.Course;
import com.growingpots.domain.university.entity.CoursePrerequisite;
import com.growingpots.domain.university.entity.CrossMajorRecognizedCourse;
import com.growingpots.domain.university.entity.Division;
import com.growingpots.domain.university.repository.CoursePrerequisiteRepository;
import com.growingpots.domain.university.repository.CourseRepository;
import com.growingpots.domain.university.repository.CrossMajorRecognizedCourseRepository;
import com.growingpots.domain.user.entity.StudentProfile;
import com.growingpots.domain.user.repository.StudentProfileRepository;
import com.growingpots.global.exception.BaseException;
import com.growingpots.global.response.error.ErrorCode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlannerService {

    private final StudentProfileRepository studentProfileRepository;
    private final StudentCourseRepository studentCourseRepository;
    private final PlannerSimulationRepository plannerSimulationRepository;
    private final PlannerTermRepository plannerTermRepository;
    private final PlannerTermVersionRepository plannerTermVersionRepository;
    private final PlannerVersionItemRepository plannerVersionItemRepository;
    private final CourseRepository courseRepository;
    private final CrossMajorRecognizedCourseRepository crossMajorRecognizedCourseRepository;
    private final CoursePrerequisiteRepository coursePrerequisiteRepository;

    @Transactional(readOnly = true)
    public PlannerResponse getPlanner(Long memberId) {
        StudentProfile profile = studentProfileRepository.findWithDetailsByMemberId(memberId)
                .orElseThrow(() -> new BaseException(ErrorCode.STUDENT_PROFILE_NOT_FOUND));

        // 재수강 과목 ID: 이수완료/수강중이면서 플래너에도 담긴 과목.
        // completedTerms totalCredit에서 제외해 해당 학기에서 유효하게 인정되는 학점만 표시한다.
        Set<Long> plannedRetakeCourseIds = computePlannedRetakeCourseIds(profile);

        return PlannerResponse.builder()
                .completedTerms(buildCompletedTerms(profile, plannedRetakeCourseIds))
                .plannedTerms(buildPlannedTerms(profile))
                .build();
    }

    // 플래너에 담긴 과목 중 이미 COMPLETED·IN_PROGRESS인 과목 ID 집합(재수강 대상).
    // buildPlannedTerms의 computeRetakeDisplay와 동일한 기준이며, completedTerms 집계에도
    // 필요해 별도 메서드로 분리한다.
    private Set<Long> computePlannedRetakeCourseIds(StudentProfile profile) {
        PlannerSimulation simulation = plannerSimulationRepository.findByStudentProfile(profile).orElse(null);
        if (simulation == null) return Set.of();

        List<PlannerTerm> terms = plannerTermRepository.findByPlannerSimulation(simulation);
        if (terms.isEmpty()) return Set.of();

        List<PlannerTermVersion> versions = plannerTermVersionRepository.findByPlannerTermIn(terms);
        if (versions.isEmpty()) return Set.of();

        List<PlannerVersionItem> items = plannerVersionItemRepository
                .findWithDetailsByPlannerTermVersionIn(versions);
        if (items.isEmpty()) return Set.of();

        Set<Long> completedOrInProgress = new HashSet<>(
                studentCourseRepository.findCourseIdsByStudentProfileAndStatusIn(
                        profile, List.of(CourseStatus.COMPLETED, CourseStatus.IN_PROGRESS)));

        return items.stream()
                .map(i -> i.getCourse().getId())
                .filter(completedOrInProgress::contains)
                .collect(Collectors.toSet());
    }

    // 아직 한 번도 저장 안 한 학생은 PLANNER_SIMULATION 자체가 없어서 빈 배열을 반환한다.
    private List<PlannerResponse.PlannedTerm> buildPlannedTerms(StudentProfile profile) {
        PlannerSimulation simulation = plannerSimulationRepository.findByStudentProfile(profile).orElse(null);
        if (simulation == null) {
            return List.of();
        }

        List<PlannerTerm> terms = plannerTermRepository.findByPlannerSimulation(simulation).stream()
                .sorted(Comparator.comparingInt(PlannerTerm::getYearLevel)
                        .thenComparingInt(t -> semesterOrder(t.getSemester())))
                .toList();
        if (terms.isEmpty()) {
            return List.of();
        }

        List<PlannerTermVersion> versions = plannerTermVersionRepository.findByPlannerTermIn(terms);
        List<PlannerVersionItem> items = versions.isEmpty()
                ? List.of()
                : plannerVersionItemRepository.findWithDetailsByPlannerTermVersionIn(versions);

        Map<Long, List<PlannerVersionItem>> itemsByVersionId = items.stream()
                .collect(Collectors.groupingBy(item -> item.getPlannerTermVersion().getId()));
        Map<Long, List<PlannerTermVersion>> versionsByTermId = versions.stream()
                .collect(Collectors.groupingBy(v -> v.getPlannerTerm().getId()));

        Map<Long, RetakeDisplay> retakeDisplayByItemId =
                computeRetakeDisplay(profile, terms, items, versionsByTermId);

        return terms.stream()
                .map(term -> toPlannedTerm(term, versionsByTermId.getOrDefault(term.getId(), List.of()),
                        itemsByVersionId, retakeDisplayByItemId))
                .toList();
    }

    // 플래너 항목별 재수강 표시 유형 계산.
    // COMPLETED·IN_PROGRESS 과목이 플래너에 담겨 있으면 재수강이고,
    // 동일 과목의 인스턴스 중 max(yearLevel, semester) 학기만 BADGE, 나머지는 DIMMED.
    private Map<Long, RetakeDisplay> computeRetakeDisplay(
            StudentProfile profile,
            List<PlannerTerm> terms,
            List<PlannerVersionItem> items,
            Map<Long, List<PlannerTermVersion>> versionsByTermId
    ) {
        if (items.isEmpty()) {
            return Map.of();
        }

        Set<Long> retakeCourseIds = new HashSet<>(
                studentCourseRepository.findCourseIdsByStudentProfileAndStatusIn(
                        profile, List.of(CourseStatus.COMPLETED, CourseStatus.IN_PROGRESS)));

        if (retakeCourseIds.isEmpty()) {
            return Map.of();
        }

        // versionId → PlannerTerm 역방향 맵. 이미 로드된 데이터만 사용해 lazy load 없이 구성.
        Map<Long, PlannerTerm> termByVersionId = new HashMap<>();
        for (PlannerTerm term : terms) {
            for (PlannerTermVersion v : versionsByTermId.getOrDefault(term.getId(), List.of())) {
                termByVersionId.put(v.getId(), term);
            }
        }

        // courseId → 해당 과목의 모든 플래너 항목 (재수강 대상 과목만)
        Map<Long, List<PlannerVersionItem>> retakeItemsByCourseId = items.stream()
                .filter(i -> retakeCourseIds.contains(i.getCourse().getId()))
                .collect(Collectors.groupingBy(i -> i.getCourse().getId()));

        Map<Long, RetakeDisplay> result = new HashMap<>();
        for (List<PlannerVersionItem> courseItems : retakeItemsByCourseId.values()) {
            PlannerTerm latestTerm = courseItems.stream()
                    .map(i -> termByVersionId.get(i.getPlannerTermVersion().getId()))
                    .filter(Objects::nonNull)
                    .max(Comparator.comparingInt(PlannerTerm::getYearLevel)
                                   .thenComparingInt(t -> semesterOrder(t.getSemester())))
                    .orElse(null);
            if (latestTerm == null) continue;

            for (PlannerVersionItem item : courseItems) {
                PlannerTerm term = termByVersionId.get(item.getPlannerTermVersion().getId());
                if (term == null) continue;
                boolean isLatest = term.getYearLevel() == latestTerm.getYearLevel()
                                && term.getSemester() == latestTerm.getSemester();
                result.put(item.getId(), isLatest ? RetakeDisplay.BADGE : RetakeDisplay.DIMMED);
            }
        }
        return result;
    }

    private PlannerResponse.PlannedTerm toPlannedTerm(
            PlannerTerm term,
            List<PlannerTermVersion> versions,
            Map<Long, List<PlannerVersionItem>> itemsByVersionId,
            Map<Long, RetakeDisplay> retakeDisplayByItemId
    ) {
        List<PlannerResponse.Version> versionResponses = versions.stream()
                .sorted(Comparator.comparingInt(PlannerTermVersion::getVersionOrder))
                .map(version -> toVersion(version, itemsByVersionId.getOrDefault(version.getId(), List.of()),
                        retakeDisplayByItemId))
                .toList();

        return PlannerResponse.PlannedTerm.builder()
                .plannerTermId(term.getId())
                .yearLevel(term.getYearLevel())
                .semester(term.getSemester())
                .versions(versionResponses)
                .build();
    }

    private PlannerResponse.Version toVersion(
            PlannerTermVersion version,
            List<PlannerVersionItem> items,
            Map<Long, RetakeDisplay> retakeDisplayByItemId
    ) {
        int totalCredit = items.stream()
                .filter(item -> retakeDisplayByItemId.get(item.getId()) != RetakeDisplay.DIMMED)
                .mapToInt(PlannerVersionItem::getCredit).sum();
        return PlannerResponse.Version.builder()
                .plannerTermVersionId(version.getId())
                .versionNo(version.getVersionNo())
                .name(version.getName())
                .isSelected(version.isSelected())
                .versionOrder(version.getVersionOrder())
                .totalCredit(totalCredit)
                .courses(items.stream()
                        .map(this::toPlannedCourse)
                        .toList())
                .build();
    }

    // retakeDisplay(BADGE/DIMMED)는 응답에서 뺐지만(#238) totalCredit 계산(toVersion)엔 여전히
    // 필요해서 computeRetakeDisplay()/retakeDisplayByItemId 로직 자체는 그대로 둔다.
    private PlannerResponse.PlannedCourse toPlannedCourse(PlannerVersionItem item) {
        Course course = item.getCourse();
        Division division = item.getPlannedDivision();

        return PlannerResponse.PlannedCourse.builder()
                .plannerVersionItemId(item.getId())
                .courseId(course.getId())
                .name(course.getName())
                .departmentName(departmentName(course))
                .divisionCategory(division != null ? division.getCategory().name() : null)
                .divisionName(division != null ? division.getCategory().getDisplayName() : null)
                .recommendedYearLow(course.getRecommendedYearLow())
                .recommendedYearHigh(course.getRecommendedYearHigh())
                .openedSemester(course.getOpenedSemester() != null ? course.getOpenedSemester().name() : null)
                .credit(item.getCredit())
                .coursePositionOrder(item.getCoursePositionOrder())
                .isEnglish(course.isEnglish())
                .isSw(course.isSw())
                .build();
    }

    // 실제로 과목을 들은 (수강년도, 수강학기) 묶음만 달력 순으로 줄 세워서 몇 번째 학기인지로
    // 학년/학기를 매긴다. "입학년도 - 수강년도" 같은 달력 계산은 휴학/유급 등으로 공백이 생기면
    // 틀어지지만(예: 1년 휴학하면 실제 3학년 2학기가 4학년 1학기로 밀림), 이 방식은 휴학한 학기엔
    // 애초에 STUDENT_COURSE 기록 자체가 없어서 순서에서 자동으로 빠지므로 안전하다.
    private List<PlannerResponse.CompletedTerm> buildCompletedTerms(
            StudentProfile profile, Set<Long> plannedRetakeCourseIds) {
        List<StudentCourse> courses = studentCourseRepository.findWithCourseAndDivisionByStudentProfile(profile);

        Map<RawTermKey, List<StudentCourse>> grouped = new TreeMap<>();
        for (StudentCourse course : courses) {
            RawTermKey key = toRawTermKey(course);
            // 수강년도/학기 정보가 없어 학기를 특정할 수 없는 과목은 플래너에 배치할 수 없어 제외한다.
            if (key == null) {
                continue;
            }
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(course);
        }

        List<PlannerResponse.CompletedTerm> result = new ArrayList<>();
        int regularCount = 0;
        int currentYearLevel = 1;
        for (Map.Entry<RawTermKey, List<StudentCourse>> entry : grouped.entrySet()) {
            Semester sem = entry.getKey().takenSemester();
            if (sem == Semester.FIRST || sem == Semester.SECOND) {
                regularCount++;
                currentYearLevel = (regularCount - 1) / 2 + 1;
            }
            int apiSemester = switch (sem) {
                case FIRST -> 1;
                case SECOND -> 2;
                case SUMMER -> 3;
                case WINTER -> 4;
            };
            // 순번을 그대로 합성 ID로 쓴다(음수) - yearLevel*10+semester 조합 공식은 정규학기 없이
            // 계절학기만 여러 해에 걸쳐 반복되는 학생의 경우 같은 (yearLevel, semester) 조합이
            // 중복돼 ID가 겹치는 문제가 있었다. 순번은 항상 유일하므로 이런 충돌이 구조적으로 없다.
            result.add(toCompletedTerm(currentYearLevel, apiSemester, entry.getValue(), plannedRetakeCourseIds,
                    -(result.size() + 1L)));
        }
        return result;
    }

    private RawTermKey toRawTermKey(StudentCourse course) {
        Integer takenYear = course.getTakenYear();
        Semester takenSemester = course.getTakenSemester();
        if (takenYear == null || takenSemester == null) {
            return null;
        }
        return new RawTermKey(takenYear, takenSemester);
    }

    private PlannerResponse.CompletedTerm toCompletedTerm(int yearLevel, int semester,
            List<StudentCourse> courses, Set<Long> plannedRetakeCourseIds, long syntheticId) {
        boolean inProgress = courses.stream().anyMatch(c -> c.getStatus() == CourseStatus.IN_PROGRESS);
        // 플래너에서 재수강 계획된 과목은 해당 학기 totalCredit에서 제외한다.
        // 재수강 시 기존 학기 학점이 대체되므로 그 학기의 유효 학점에서 빠져야 한다.
        int totalCredit = courses.stream()
                .filter(c -> c.getCourse() == null
                        || !plannedRetakeCourseIds.contains(c.getCourse().getId()))
                .mapToInt(StudentCourse::getCredit).sum();

        return PlannerResponse.CompletedTerm.builder()
                .yearLevel(yearLevel)
                .semester(semester)
                // completedTerms엔 실제 PLANNER_TERM_VERSION row가 없어 합성 ID를 만들어 넣는다.
                // AUTO_INCREMENT PK(항상 양수)와 절대 안 겹치도록 음수로 둔다 — 실수로 진짜 PK처럼
                // 다른 API에 넘겨져도(예: 저장/삭제) DB에 없는 값이라 즉시 실패하도록 하기 위함.
                .plannerTermVersionId(syntheticId)
                .name(yearLevel + "학년 " + semesterDisplayName(semester))
                .status(inProgress ? "IN_PROGRESS" : "COMPLETED")
                .totalCredit(totalCredit)
                .courses(courses.stream().map(this::toCompletedCourse).toList())
                .build();
    }

    private PlannerResponse.CompletedCourse toCompletedCourse(StudentCourse studentCourse) {
        Course course = studentCourse.getCourse();
        Division division = studentCourse.getAppliedDivision();

        return PlannerResponse.CompletedCourse.builder()
                .studentCourseId(studentCourse.getId())
                .courseId(course != null ? course.getId() : null)
                // rawCourseName은 TranscriptPersister가 매칭 시 COURSE 이름으로 정리해서 저장하고,
                // 사용자가 편집 화면에서 고치면 그 값으로 덮어써진다. 여기서 course.getName()으로
                // 다시 덮어쓰면 사용자가 고친 이름이 무시되므로 항상 rawCourseName을 그대로 쓴다.
                .name(studentCourse.getRawCourseName())
                .departmentName(departmentName(course))
                .divisionCategory(division != null ? division.getCategory().name() : null)
                .divisionName(division != null ? division.getCategory().getDisplayName() : null)
                .recommendedYearLow(course != null ? course.getRecommendedYearLow() : null)
                .recommendedYearHigh(course != null ? course.getRecommendedYearHigh() : null)
                .openedSemester(course != null && course.getOpenedSemester() != null ? course.getOpenedSemester().name() : null)
                .credit(studentCourse.getCredit())
                .isEnglish(course != null && course.isEnglish())
                .isSw(course != null && course.isSw())
                .build();
    }

    private String departmentName(Course course) {
        if (course == null || course.getOfferingDepartment() == null) {
            return null;
        }
        return course.getOfferingDepartment().getName();
    }

    // 달력 기준 수강년도/학기 묶음. 학년/학기 표시값이 아니라 정렬 순서를 정하기 위한 원시 키다.
    private record RawTermKey(int takenYear, Semester takenSemester) implements Comparable<RawTermKey> {
        @Override
        public int compareTo(RawTermKey other) {
            int byYear = Integer.compare(takenYear, other.takenYear);
            return byYear != 0 ? byYear
                    : Integer.compare(chronologicalOrder(takenSemester), chronologicalOrder(other.takenSemester));
        }

        private static int chronologicalOrder(Semester semester) {
            return switch (semester) {
                case FIRST -> 0;
                case SUMMER -> 1;
                case SECOND -> 2;
                case WINTER -> 3;
            };
        }
    }

    // 플래너 API semester 값 → 시간순 정렬 인덱스 (1=1학기, 2=2학기, 3=여름, 4=겨울)
    private static int semesterOrder(int semester) {
        return switch (semester) {
            case 1 -> 0;
            case 3 -> 1;
            case 2 -> 2;
            case 4 -> 3;
            default -> semester;
        };
    }

    private static String semesterDisplayName(int semester) {
        return switch (semester) {
            case 1 -> "1학기";
            case 2 -> "2학기";
            case 3 -> "여름학기";
            case 4 -> "겨울학기";
            default -> semester + "학기";
        };
    }

    @Transactional
    public boolean savePlanner(Long memberId, PlannerSaveRequest request) {
        StudentProfile profile = studentProfileRepository.findWithDetailsByMemberId(memberId)
                .orElseThrow(() -> new BaseException(ErrorCode.STUDENT_PROFILE_NOT_FOUND));

        PlannerSimulation simulation = resolveSimulation(request.plannerSimulationId(), profile);

        validateVersions(request);

        Map<Long, Course> courseMap = loadCourseMap(request, profile);

        Set<Long> beforeCourseIds = plannerVersionItemRepository.findSelectedByStudentProfile(profile)
                .stream()
                .map(i -> i.getCourse().getId())
                .collect(Collectors.toSet());

        deleteExistingData(simulation.getId());

        buildAndSave(simulation, request, courseMap, profile);

        return computeHasDuplicateCourse(profile, request, beforeCourseIds);
    }

    private boolean computeHasDuplicateCourse(
            StudentProfile profile,
            PlannerSaveRequest request,
            Set<Long> beforeCourseIds
    ) {
        Set<Long> requestSelectedCourseIds = request.terms().stream()
                .flatMap(t -> t.versions().stream())
                .filter(v -> Boolean.TRUE.equals(v.isSelected()))
                .filter(v -> v.items() != null)
                .flatMap(v -> v.items().stream())
                .map(PlannerSaveRequest.ItemRequest::courseId)
                .collect(Collectors.toSet());

        Set<Long> newlyAdded = new HashSet<>(requestSelectedCourseIds);
        newlyAdded.removeAll(beforeCourseIds);

        if (newlyAdded.isEmpty()) return false;

        Set<Long> alreadyTaken = new HashSet<>(
                studentCourseRepository.findCourseIdsByStudentProfileAndStatusIn(
                        profile, List.of(CourseStatus.COMPLETED, CourseStatus.IN_PROGRESS)));

        return newlyAdded.stream().anyMatch(alreadyTaken::contains);
    }

    // 학생당 시뮬레이션은 1개뿐이라, id 없이 저장 요청이 오면 새로 만들기 전에 기존 걸 먼저 찾는다
    // (안 그러면 두 번째 저장부터 studentProfile 유니크 제약에 걸린다).
    private PlannerSimulation resolveSimulation(Long simulationId, StudentProfile profile) {
        if (simulationId == null) {
            return plannerSimulationRepository.findByStudentProfile(profile)
                    .orElseGet(() -> plannerSimulationRepository.save(
                            PlannerSimulation.builder()
                                    .studentProfile(profile)
                                    .name("내 플래너")
                                    .build()
                    ));
        }
        PlannerSimulation simulation = plannerSimulationRepository.findById(simulationId)
                .orElseThrow(() -> new BaseException(ErrorCode.PLANNER_NOT_FOUND));
        if (!simulation.getStudentProfile().getId().equals(profile.getId())) {
            throw new BaseException(ErrorCode.PLANNER_ACCESS_DENIED);
        }
        return simulation;
    }

    // 학생 학과 기준 타전공 인정 이수구분이 있으면 그걸, 없으면 과목 자체의 기본 이수구분을 쓴다
    // (CourseService.searchCourses의 인정 이수구분 조회 로직과 동일).
    private Map<Long, Division> loadRecognizedDivisionByCourseId(StudentProfile profile) {
        List<CrossMajorRecognizedCourse> recognized =
                crossMajorRecognizedCourseRepository.findByTargetDepartment(profile.getDepartment());
        Map<Long, Division> recognizedDivisionByCourseId = new HashMap<>();
        for (CrossMajorRecognizedCourse r : recognized) {
            recognizedDivisionByCourseId.put(r.getCourse().getId(), r.getRecognizedDivision());
        }
        return recognizedDivisionByCourseId;
    }

    private void validateVersions(PlannerSaveRequest request) {
        Set<String> termKeys = new HashSet<>();
        for (PlannerSaveRequest.TermRequest termReq : request.terms()) {
            if (!termKeys.add(termReq.yearLevel() + "_" + termReq.semester())) {
                throw new BaseException(ErrorCode.PLANNER_INVALID_DATA);
            }
            long selectedCount = termReq.versions().stream()
                    .filter(v -> Boolean.TRUE.equals(v.isSelected()))
                    .count();
            if (selectedCount != 1) {
                throw new BaseException(ErrorCode.PLANNER_INVALID_DATA);
            }
            Set<Integer> seen = new HashSet<>();
            Set<Integer> seenOrders = new HashSet<>();
            for (PlannerSaveRequest.VersionRequest versionReq : termReq.versions()) {
                if (!seen.add(versionReq.versionNo())) {
                    throw new BaseException(ErrorCode.PLANNER_INVALID_DATA);
                }
                if (!seenOrders.add(versionReq.versionOrder())) {
                    throw new BaseException(ErrorCode.PLANNER_INVALID_DATA);
                }
                if (versionReq.items() != null) {
                    Set<Long> seenCourseIds = new HashSet<>();
                    Set<Integer> seenPositionOrders = new HashSet<>();
                    for (PlannerSaveRequest.ItemRequest itemReq : versionReq.items()) {
                        if (!seenCourseIds.add(itemReq.courseId())) {
                            throw new BaseException(ErrorCode.PLANNER_DUPLICATE_COURSE);
                        }
                        if (!seenPositionOrders.add(itemReq.coursePositionOrder())) {
                            throw new BaseException(ErrorCode.PLANNER_INVALID_DATA);
                        }
                    }
                }
            }
        }
    }

    private Map<Long, Course> loadCourseMap(PlannerSaveRequest request, StudentProfile profile) {
        Set<Long> courseIds = request.terms().stream()
                .flatMap(t -> t.versions().stream())
                .flatMap(v -> v.items() == null ? java.util.stream.Stream.empty() : v.items().stream())
                .map(PlannerSaveRequest.ItemRequest::courseId)
                .collect(Collectors.toSet());

        Map<Long, Course> map = courseRepository.findAllById(courseIds).stream()
                .collect(Collectors.toMap(Course::getId, Function.identity()));

        Long schoolId = profile.getSchool().getId();
        for (Long id : courseIds) {
            Course course = map.get(id);
            if (course == null) throw new BaseException(ErrorCode.COURSE_NOT_FOUND);
            if (!course.getSchool().getId().equals(schoolId)) {
                throw new BaseException(ErrorCode.COURSE_NOT_FOUND);
            }
        }
        return map;
    }

    private void deleteExistingData(Long simulationId) {
        List<Long> termIds = plannerTermRepository.findIdsByPlannerSimulationId(simulationId);
        if (termIds.isEmpty()) return;
        List<Long> versionIds = plannerTermVersionRepository.findIdsByPlannerTermIdIn(termIds);
        if (!versionIds.isEmpty()) {
            plannerVersionItemRepository.deleteAllByPlannerTermVersionIdIn(versionIds);
        }
        plannerTermVersionRepository.deleteAllByPlannerTermIdIn(termIds);
        plannerTermRepository.deleteAllByPlannerSimulationId(simulationId);
    }

    private void buildAndSave(
            PlannerSimulation simulation,
            PlannerSaveRequest request,
            Map<Long, Course> courseMap,
            StudentProfile profile
    ) {
        Map<Long, Division> recognizedDivisionByCourseId = loadRecognizedDivisionByCourseId(profile);

        List<PlannerSaveRequest.TermRequest> sortedTerms = request.terms().stream()
                .sorted(Comparator.comparingInt(PlannerSaveRequest.TermRequest::yearLevel)
                        .thenComparingInt(t -> semesterOrder(t.semester())))
                .toList();

        for (PlannerSaveRequest.TermRequest termReq : sortedTerms) {
            PlannerTerm term = plannerTermRepository.save(
                    PlannerTerm.builder()
                            .plannerSimulation(simulation)
                            .yearLevel(termReq.yearLevel())
                            .semester(termReq.semester())
                            .build()
            );

            for (PlannerSaveRequest.VersionRequest versionReq : termReq.versions()) {
                PlannerTermVersion version = plannerTermVersionRepository.save(
                        PlannerTermVersion.builder()
                                .plannerTerm(term)
                                .versionNo(versionReq.versionNo())
                                .name(versionReq.name())
                                .isSelected(versionReq.isSelected())
                                .versionOrder(versionReq.versionOrder())
                                .build()
                );

                List<PlannerSaveRequest.ItemRequest> items =
                        versionReq.items() != null ? versionReq.items() : List.of();

                for (PlannerSaveRequest.ItemRequest itemReq : items) {
                    Course course = courseMap.get(itemReq.courseId());
                    Division plannedDivision = recognizedDivisionByCourseId
                            .getOrDefault(course.getId(), course.getDefaultDivision());
                    plannerVersionItemRepository.save(
                            PlannerVersionItem.builder()
                                    .plannerTermVersion(version)
                                    .course(course)
                                    .plannedDivision(plannedDivision)
                                    .credit(course.getCredit())
                                    .coursePositionOrder(itemReq.coursePositionOrder())
                                    .build()
                    );
                }
            }
        }
    }

    @Transactional
    public SelectVersionResponse selectVersion(Long memberId, Long plannerTermId, SelectVersionRequest request) {
        StudentProfile profile = studentProfileRepository.findWithDetailsByMemberId(memberId)
                .orElseThrow(() -> new BaseException(ErrorCode.STUDENT_PROFILE_NOT_FOUND));

        PlannerTerm term = plannerTermRepository.findWithOwnerById(plannerTermId)
                .filter(t -> t.getPlannerSimulation().getStudentProfile().getId().equals(profile.getId()))
                .orElseThrow(() -> new BaseException(ErrorCode.PLANNER_TERM_NOT_FOUND));

        if (isTermLocked(term)) {
            throw new BaseException(ErrorCode.PLANNER_TERM_LOCKED);
        }

        Long versionId = request.plannerTermVersionId();
        if (!plannerTermVersionRepository.existsByIdAndPlannerTermId(versionId, plannerTermId)) {
            throw new BaseException(ErrorCode.PLANNER_TERM_NOT_FOUND);
        }

        plannerTermVersionRepository.deselectAllByTermId(plannerTermId);
        plannerTermVersionRepository.selectById(versionId);

        return new SelectVersionResponse(plannerTermId, versionId);
    }

    // plannedTerms(PLANNER_TERM)는 정상 흐름에서 항상 미래 학기만 존재한다 — 이미 지난/진행중인
    // 학기는 STUDENT_COURSE 기반 GET /planner의 completedTerms로 빠지고 PLANNER_TERM으로 안
    // 남는다(GET /planner 구현 완료, PlannerService.getPlanner 참고). 그래서 원칙적으론 "잠글"
    // 대상 자체가 없어 false 고정이 맞다. 다만 저장 API가 과거 학기 저장을 막지 않아 예외적으로
    // 그런 PlannerTerm이 남을 수 있는데, 필요해지면 그때 StudentProfile.currentGrade/currentTerm과
    // 비교해서 판정하면 된다.
    private boolean isTermLocked(PlannerTerm term) {
        return false;
    }

    private void validateCoursesOwnedBySchool(List<Long> courseIds, Long schoolId) {
        Map<Long, Course> courseMap = courseRepository.findAllById(courseIds).stream()
                .collect(Collectors.toMap(Course::getId, Function.identity()));
        for (Long id : courseIds) {
            Course course = courseMap.get(id);
            if (course == null || !course.getSchool().getId().equals(schoolId)) {
                throw new BaseException(ErrorCode.COURSE_NOT_FOUND);
            }
        }
    }

    @Transactional(readOnly = true)
    public PrerequisiteCheckResponse checkPrerequisites(Long memberId, PrerequisiteCheckRequest request) {
        StudentProfile profile = studentProfileRepository.findWithDetailsByMemberId(memberId)
                .orElseThrow(() -> new BaseException(ErrorCode.STUDENT_PROFILE_NOT_FOUND));

        Long departmentId = profile.getDepartment().getId();
        List<Long> courseIds = request.courseIds();

        validateCoursesOwnedBySchool(courseIds, profile.getSchool().getId());

        // Query 1: prerequisites with course/requiredCourse names via JOIN FETCH
        List<CoursePrerequisite> prerequisites =
                coursePrerequisiteRepository.findByCourseIdsAndDepartment(courseIds, departmentId);

        // 같은 (course, requiredCourse) 쌍에서 학과 특정 규칙이 공통(null) 규칙보다 우선
        Map<String, CoursePrerequisite> bestByPair = prerequisites.stream()
                .collect(Collectors.toMap(
                        cp -> cp.getCourse().getId() + "_" + cp.getRequiredCourse().getId(),
                        cp -> cp,
                        (a, b) -> a.getDepartment() != null ? a : b
                ));

        // Query 2: COMPLETED + IN_PROGRESS 과목 ID → 이수로 간주
        Set<Long> takenCourseIds = new HashSet<>(studentCourseRepository
                .findCourseIdsByStudentProfileAndStatusIn(
                        profile, List.of(CourseStatus.COMPLETED, CourseStatus.IN_PROGRESS)));

        // plannerTermId가 제공된 경우: 해당 학기보다 이전 학기의 플래너 과목도 이수 예정으로 간주
        if (request.plannerTermId() != null) {
            PlannerTerm targetTerm = plannerTermRepository.findWithOwnerById(request.plannerTermId())
                    .orElseThrow(() -> new BaseException(ErrorCode.PLANNER_TERM_NOT_FOUND));
            if (!targetTerm.getPlannerSimulation().getStudentProfile().getId().equals(profile.getId())) {
                throw new BaseException(ErrorCode.PLANNER_ACCESS_DENIED);
            }
            List<Long> plannedCourseIds = plannerVersionItemRepository.findCourseIdsInEarlierTerms(
                    targetTerm.getPlannerSimulation(),
                    targetTerm.getYearLevel() * 10 + semesterOrder(targetTerm.getSemester()));
            takenCourseIds.addAll(plannedCourseIds);
        }

        // 미이수 선수과목만 필터링 후 과목별 그룹핑
        Map<Long, List<CoursePrerequisite>> missingByCourseId = bestByPair.values().stream()
                .filter(cp -> !takenCourseIds.contains(cp.getRequiredCourse().getId()))
                .collect(Collectors.groupingBy(cp -> cp.getCourse().getId()));

        List<PrerequisiteCheckResponse.CourseResult> results = missingByCourseId.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    CoursePrerequisite first = entry.getValue().get(0);
                    List<PrerequisiteCheckResponse.MissingPrerequisite> missing = entry.getValue().stream()
                            .sorted(Comparator.comparing(cp -> cp.getRequiredCourse().getId()))
                            .map(cp -> new PrerequisiteCheckResponse.MissingPrerequisite(
                                    cp.getRequiredCourse().getId(),
                                    cp.getRequiredCourse().getName(),
                                    cp.getPrerequisiteType()
                            ))
                            .toList();
                    return new PrerequisiteCheckResponse.CourseResult(
                            entry.getKey(), first.getCourse().getName(), missing);
                })
                .toList();

        return new PrerequisiteCheckResponse(results);
    }
}