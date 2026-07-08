package com.growingpots.domain.planner.service;

import com.growingpots.domain.planner.dto.request.PlannerSaveRequest;
import com.growingpots.domain.planner.dto.request.PrerequisiteCheckRequest;
import com.growingpots.domain.planner.dto.request.SelectVersionRequest;
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
import com.growingpots.domain.transcript.entity.enums.CourseStatus;
import com.growingpots.domain.transcript.repository.StudentCourseRepository;
import com.growingpots.domain.university.entity.Course;
import com.growingpots.domain.university.entity.CoursePrerequisite;
import com.growingpots.domain.university.entity.enums.OpenedSemester;
import com.growingpots.domain.university.repository.CoursePrerequisiteRepository;
import com.growingpots.domain.university.repository.CourseRepository;
import com.growingpots.domain.user.entity.StudentProfile;
import com.growingpots.domain.user.repository.StudentProfileRepository;
import com.growingpots.global.exception.BaseException;
import com.growingpots.global.response.error.ErrorCode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlannerService {

    private final StudentProfileRepository studentProfileRepository;
    private final PlannerSimulationRepository plannerSimulationRepository;
    private final PlannerTermRepository plannerTermRepository;
    private final PlannerTermVersionRepository plannerTermVersionRepository;
    private final PlannerVersionItemRepository plannerVersionItemRepository;
    private final CourseRepository courseRepository;
    private final CoursePrerequisiteRepository coursePrerequisiteRepository;
    private final StudentCourseRepository studentCourseRepository;

    @Transactional
    public PlannerSaveResponse savePlanner(Long memberId, PlannerSaveRequest request) {
        StudentProfile profile = studentProfileRepository.findWithDetailsByMemberId(memberId)
                .orElseThrow(() -> new BaseException(ErrorCode.STUDENT_PROFILE_NOT_FOUND));

        PlannerSimulation simulation = resolveSimulation(request.plannerSimulationId(), profile);

        validateVersions(request);

        Map<Long, Course> courseMap = loadCourseMap(request, profile);

        validateSemesterCompatibility(request, courseMap);

        deleteExistingData(simulation.getId());

        return buildAndSave(simulation, request, courseMap);
    }

    private PlannerSimulation resolveSimulation(Long simulationId, StudentProfile profile) {
        if (simulationId == null) {
            return plannerSimulationRepository.save(
                    PlannerSimulation.builder()
                            .studentProfile(profile)
                            .name("내 플래너")
                            .build()
            );
        }
        PlannerSimulation simulation = plannerSimulationRepository.findById(simulationId)
                .orElseThrow(() -> new BaseException(ErrorCode.PLANNER_NOT_FOUND));
        if (!simulation.getStudentProfile().getId().equals(profile.getId())) {
            throw new BaseException(ErrorCode.PLANNER_ACCESS_DENIED);
        }
        return simulation;
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
            for (PlannerSaveRequest.VersionRequest versionReq : termReq.versions()) {
                if (!seen.add(versionReq.versionNo())) {
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

    private void validateSemesterCompatibility(PlannerSaveRequest request, Map<Long, Course> courseMap) {
        for (PlannerSaveRequest.TermRequest termReq : request.terms()) {
            for (PlannerSaveRequest.VersionRequest versionReq : termReq.versions()) {
                if (versionReq.items() == null) continue;
                for (PlannerSaveRequest.ItemRequest itemReq : versionReq.items()) {
                    Course course = courseMap.get(itemReq.courseId());
                    OpenedSemester openedSemester = course.getOpenedSemester();
                    if (openedSemester == OpenedSemester.BOTH) continue;
                    if (openedSemester == OpenedSemester.FIRST && termReq.semester() != 1) {
                        throw new BaseException(ErrorCode.PLANNER_INVALID_DATA);
                    }
                    if (openedSemester == OpenedSemester.SECOND && termReq.semester() != 2) {
                        throw new BaseException(ErrorCode.PLANNER_INVALID_DATA);
                    }
                }
            }
        }
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
            Map<Long, Course> courseMap
    ) {
        List<PlannerSaveResponse.TermResponse> termResponses = new ArrayList<>();

        for (PlannerSaveRequest.TermRequest termReq : request.terms()) {
            PlannerTerm term = plannerTermRepository.save(
                    PlannerTerm.builder()
                            .plannerSimulation(simulation)
                            .yearLevel(termReq.yearLevel())
                            .semester(termReq.semester())
                            .termOrder(termReq.termOrder())
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
                                .build()
                );

                List<PlannerSaveResponse.ItemResponse> itemResponses = new ArrayList<>();
                List<PlannerSaveRequest.ItemRequest> items =
                        versionReq.items() != null ? versionReq.items() : List.of();

                for (PlannerSaveRequest.ItemRequest itemReq : items) {
                    Course course = courseMap.get(itemReq.courseId());
                    PlannerVersionItem item = plannerVersionItemRepository.save(
                            PlannerVersionItem.builder()
                                    .plannerTermVersion(version)
                                    .course(course)
                                    .credit(course.getCredit())
                                    .positionOrder(itemReq.positionOrder())
                                    .build()
                    );
                    itemResponses.add(new PlannerSaveResponse.ItemResponse(item.getId(), course.getId()));
                }

                versionResponses.add(new PlannerSaveResponse.VersionResponse(
                        version.getId(), version.getVersionNo(), version.isSelected(), itemResponses));
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

    // TODO(#GET-planner): GET /planner 구현 시 실제 locked 판정 로직으로 교체 필요
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