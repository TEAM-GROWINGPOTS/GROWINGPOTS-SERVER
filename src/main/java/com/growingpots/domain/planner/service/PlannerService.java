package com.growingpots.domain.planner.service;

import com.growingpots.domain.planner.dto.request.PlannerSaveRequest;
import com.growingpots.domain.planner.dto.request.PrerequisiteCheckRequest;
import com.growingpots.domain.planner.dto.request.SelectVersionRequest;
import com.growingpots.domain.planner.dto.response.PlannerResponse;
import com.growingpots.domain.planner.dto.response.PlannerSaveResponse;
import com.growingpots.domain.planner.dto.response.PrerequisiteCheckResponse;
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

        return PlannerResponse.builder()
                .completedTerms(buildCompletedTerms(profile))
                .plannedTerms(buildPlannedTerms(profile))
                .build();
    }

    // 아직 한 번도 저장 안 한 학생은 PLANNER_SIMULATION 자체가 없어서 빈 배열을 반환한다.
    private List<PlannerResponse.PlannedTerm> buildPlannedTerms(StudentProfile profile) {
        PlannerSimulation simulation = plannerSimulationRepository.findByStudentProfile(profile).orElse(null);
        if (simulation == null) {
            return List.of();
        }

        List<PlannerTerm> terms = plannerTermRepository.findByPlannerSimulationOrderByYearLevelAscSemesterAsc(simulation);
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

        return terms.stream()
                .map(term -> toPlannedTerm(term, versionsByTermId.getOrDefault(term.getId(), List.of()), itemsByVersionId))
                .toList();
    }

    private PlannerResponse.PlannedTerm toPlannedTerm(
            PlannerTerm term, List<PlannerTermVersion> versions, Map<Long, List<PlannerVersionItem>> itemsByVersionId) {
        List<PlannerResponse.Version> versionResponses = versions.stream()
                .sorted(Comparator.comparingInt(PlannerTermVersion::getVersionOrder))
                .map(version -> toVersion(version, itemsByVersionId.getOrDefault(version.getId(), List.of())))
                .toList();

        return PlannerResponse.PlannedTerm.builder()
                .plannerTermId(term.getId())
                .yearLevel(term.getYearLevel())
                .semester(term.getSemester())
                .versions(versionResponses)
                .build();
    }

    private PlannerResponse.Version toVersion(PlannerTermVersion version, List<PlannerVersionItem> items) {
        int totalCredit = items.stream().mapToInt(PlannerVersionItem::getCredit).sum();
        return PlannerResponse.Version.builder()
                .plannerTermVersionId(version.getId())
                .versionNo(version.getVersionNo())
                .name(version.getName())
                .isSelected(version.isSelected())
                .versionOrder(version.getVersionOrder())
                .totalCredit(totalCredit)
                .courses(items.stream().map(this::toPlannedCourse).toList())
                .build();
    }

    private PlannerResponse.PlannedCourse toPlannedCourse(PlannerVersionItem item) {
        Course course = item.getCourse();
        Division division = item.getPlannedDivision();

        return PlannerResponse.PlannedCourse.builder()
                .plannerVersionItemId(item.getId())
                .courseId(course.getId())
                .courseName(course.getName())
                .departmentName(departmentName(course))
                .divisionCategory(division != null ? division.getCategory().name() : null)
                .divisionName(division != null ? division.getCategory().getDisplayName() : null)
                .recommendedYearLow(course.getRecommendedYearLow())
                .recommendedYearHigh(course.getRecommendedYearHigh())
                .openedSemester(course.getOpenedSemester() != null ? course.getOpenedSemester().name() : null)
                .credit(item.getCredit())
                .coursePositionOrder(item.getCoursePositionOrder())
                .build();
    }

    // 실제로 과목을 들은 (수강년도, 수강학기) 묶음만 달력 순으로 줄 세워서 몇 번째 학기인지로
    // 학년/학기를 매긴다. "입학년도 - 수강년도" 같은 달력 계산은 휴학/유급 등으로 공백이 생기면
    // 틀어지지만(예: 1년 휴학하면 실제 3학년 2학기가 4학년 1학기로 밀림), 이 방식은 휴학한 학기엔
    // 애초에 STUDENT_COURSE 기록 자체가 없어서 순서에서 자동으로 빠지므로 안전하다.
    private List<PlannerResponse.CompletedTerm> buildCompletedTerms(StudentProfile profile) {
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
        int sequence = 0;
        for (List<StudentCourse> termCourses : grouped.values()) {
            sequence++;
            int yearLevel = (sequence - 1) / 2 + 1;
            int semester = (sequence - 1) % 2 + 1;
            result.add(toCompletedTerm(yearLevel, semester, termCourses));
        }
        return result;
    }

    private RawTermKey toRawTermKey(StudentCourse course) {
        Integer takenYear = course.getTakenYear();
        Semester takenSemester = course.getTakenSemester();
        if (takenYear == null || takenSemester == null) {
            return null;
        }
        int semesterBucket = (takenSemester == Semester.FIRST || takenSemester == Semester.SUMMER) ? 1 : 2;
        return new RawTermKey(takenYear, semesterBucket);
    }

    private PlannerResponse.CompletedTerm toCompletedTerm(int yearLevel, int semester, List<StudentCourse> courses) {
        boolean inProgress = courses.stream().anyMatch(c -> c.getStatus() == CourseStatus.IN_PROGRESS);
        int totalCredit = courses.stream().mapToInt(StudentCourse::getCredit).sum();

        return PlannerResponse.CompletedTerm.builder()
                .yearLevel(yearLevel)
                .semester(semester)
                // completedTerms엔 실제 PLANNER_TERM_VERSION row가 없어 합성 ID를 만들어 넣는다.
                // AUTO_INCREMENT PK(항상 양수)와 절대 안 겹치도록 음수로 둔다 — 실수로 진짜 PK처럼
                // 다른 API에 넘겨져도(예: 저장/삭제) DB에 없는 값이라 즉시 실패하도록 하기 위함.
                .plannerTermVersionId(-(yearLevel * 10L + semester))
                .name(yearLevel + "학년 " + semester + "학기")
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
                .courseName(studentCourse.getRawCourseName())
                .departmentName(departmentName(course))
                .divisionCategory(division.getCategory().name())
                .divisionName(division.getCategory().getDisplayName())
                .recommendedYearLow(course != null ? course.getRecommendedYearLow() : null)
                .recommendedYearHigh(course != null ? course.getRecommendedYearHigh() : null)
                .openedSemester(course != null && course.getOpenedSemester() != null ? course.getOpenedSemester().name() : null)
                .credit(studentCourse.getCredit())
                .build();
    }

    private String departmentName(Course course) {
        if (course == null || course.getOfferingDepartment() == null) {
            return null;
        }
        return course.getOfferingDepartment().getName();
    }

    // 달력 기준 수강년도/학기 묶음. 학년/학기 표시값이 아니라 정렬 순서를 정하기 위한 원시 키다.
    private record RawTermKey(int takenYear, int semesterBucket) implements Comparable<RawTermKey> {
        @Override
        public int compareTo(RawTermKey other) {
            int byYear = Integer.compare(takenYear, other.takenYear);
            return byYear != 0 ? byYear : Integer.compare(semesterBucket, other.semesterBucket);
        }
    }

    @Transactional
    public PlannerSaveResponse savePlanner(Long memberId, PlannerSaveRequest request) {
        StudentProfile profile = studentProfileRepository.findWithDetailsByMemberId(memberId)
                .orElseThrow(() -> new BaseException(ErrorCode.STUDENT_PROFILE_NOT_FOUND));

        PlannerSimulation simulation = resolveSimulation(request.plannerSimulationId(), profile);

        validateVersions(request);

        Map<Long, Course> courseMap = loadCourseMap(request, profile);

        deleteExistingData(simulation.getId());

        return buildAndSave(simulation, request, courseMap, profile);
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

    private PlannerSaveResponse buildAndSave(
            PlannerSimulation simulation,
            PlannerSaveRequest request,
            Map<Long, Course> courseMap,
            StudentProfile profile
    ) {
        Map<Long, Division> recognizedDivisionByCourseId = loadRecognizedDivisionByCourseId(profile);
        List<PlannerSaveResponse.TermResponse> termResponses = new ArrayList<>();

        List<PlannerSaveRequest.TermRequest> sortedTerms = request.terms().stream()
                .sorted(Comparator.comparingInt(PlannerSaveRequest.TermRequest::yearLevel)
                        .thenComparingInt(PlannerSaveRequest.TermRequest::semester))
                .toList();

        for (PlannerSaveRequest.TermRequest termReq : sortedTerms) {
            PlannerTerm term = plannerTermRepository.save(
                    PlannerTerm.builder()
                            .plannerSimulation(simulation)
                            .yearLevel(termReq.yearLevel())
                            .semester(termReq.semester())
                            .build()
            );

            List<PlannerSaveResponse.VersionResponse> versionResponses = new ArrayList<>();
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

                List<PlannerSaveResponse.ItemResponse> itemResponses = new ArrayList<>();
                List<PlannerSaveRequest.ItemRequest> items =
                        versionReq.items() != null ? versionReq.items() : List.of();

                for (PlannerSaveRequest.ItemRequest itemReq : items) {
                    Course course = courseMap.get(itemReq.courseId());
                    Division plannedDivision = recognizedDivisionByCourseId
                            .getOrDefault(course.getId(), course.getDefaultDivision());
                    PlannerVersionItem item = plannerVersionItemRepository.save(
                            PlannerVersionItem.builder()
                                    .plannerTermVersion(version)
                                    .course(course)
                                    .plannedDivision(plannedDivision)
                                    .credit(course.getCredit())
                                    .coursePositionOrder(itemReq.coursePositionOrder())
                                    .build()
                    );
                    itemResponses.add(new PlannerSaveResponse.ItemResponse(item.getId(), course.getId(), item.getCoursePositionOrder()));
                }

                versionResponses.add(new PlannerSaveResponse.VersionResponse(
                        version.getId(), version.getVersionNo(), version.isSelected(), version.getVersionOrder(), itemResponses));
            }

            termResponses.add(new PlannerSaveResponse.TermResponse(
                    term.getId(), term.getYearLevel(), term.getSemester(), versionResponses));
        }

        return new PlannerSaveResponse(simulation.getId(), termResponses);
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