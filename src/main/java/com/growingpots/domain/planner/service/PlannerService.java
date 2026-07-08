package com.growingpots.domain.planner.service;

import com.growingpots.domain.planner.dto.request.PlannerSaveRequest;
import com.growingpots.domain.planner.dto.response.PlannerSaveResponse;
import com.growingpots.domain.planner.entity.PlannerSimulation;
import com.growingpots.domain.planner.entity.PlannerTerm;
import com.growingpots.domain.planner.entity.PlannerTermVersion;
import com.growingpots.domain.planner.entity.PlannerVersionItem;
import com.growingpots.domain.planner.repository.PlannerSimulationRepository;
import com.growingpots.domain.planner.repository.PlannerTermRepository;
import com.growingpots.domain.planner.repository.PlannerTermVersionRepository;
import com.growingpots.domain.planner.repository.PlannerVersionItemRepository;
import com.growingpots.domain.university.entity.Course;
import com.growingpots.domain.university.entity.CrossMajorRecognizedCourse;
import com.growingpots.domain.university.entity.Division;
import com.growingpots.domain.university.entity.enums.OpenedSemester;
import com.growingpots.domain.university.repository.CourseRepository;
import com.growingpots.domain.university.repository.CrossMajorRecognizedCourseRepository;
import com.growingpots.domain.user.entity.StudentProfile;
import com.growingpots.domain.user.repository.StudentProfileRepository;
import com.growingpots.global.exception.BaseException;
import com.growingpots.global.response.error.ErrorCode;
import java.util.ArrayList;
import java.util.HashMap;
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
    private final CrossMajorRecognizedCourseRepository crossMajorRecognizedCourseRepository;

    @Transactional
    public PlannerSaveResponse savePlanner(Long memberId, PlannerSaveRequest request) {
        StudentProfile profile = studentProfileRepository.findWithDetailsByMemberId(memberId)
                .orElseThrow(() -> new BaseException(ErrorCode.STUDENT_PROFILE_NOT_FOUND));

        PlannerSimulation simulation = resolveSimulation(request.plannerSimulationId(), profile);

        validateVersions(request);

        Map<Long, Course> courseMap = loadCourseMap(request, profile);

        validateSemesterCompatibility(request, courseMap);

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
            Map<Long, Course> courseMap,
            StudentProfile profile
    ) {
        Map<Long, Division> recognizedDivisionByCourseId = loadRecognizedDivisionByCourseId(profile);
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
                    Division plannedDivision = recognizedDivisionByCourseId
                            .getOrDefault(course.getId(), course.getDefaultDivision());
                    PlannerVersionItem item = plannerVersionItemRepository.save(
                            PlannerVersionItem.builder()
                                    .plannerTermVersion(version)
                                    .course(course)
                                    .plannedDivision(plannedDivision)
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
}