package com.growingpots.domain.planner.service;

import com.growingpots.domain.planner.dto.request.PlannerSaveRequest;
import com.growingpots.domain.planner.dto.response.PlannerResponse;
import com.growingpots.domain.planner.dto.response.PlannerSaveResponse;
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

        List<PlannerTerm> terms = plannerTermRepository.findByPlannerSimulationOrderByTermOrder(simulation);
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
                .sorted(Comparator.comparingInt(PlannerTermVersion::getVersionNo))
                .map(version -> toVersion(version, itemsByVersionId.getOrDefault(version.getId(), List.of())))
                .toList();

        return PlannerResponse.PlannedTerm.builder()
                .plannerTermId(term.getId())
                .yearLevel(term.getYearLevel())
                .semester(term.getSemester())
                .termOrder(term.getTermOrder())
                .locked(false)
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
                .positionOrder(item.getPositionOrder())
                .build();
    }

    // (학년, 학기) 단위로 묶는다. 여름학기는 1학기, 겨울학기는 2학기 묶음에 합산한다.
    private List<PlannerResponse.CompletedTerm> buildCompletedTerms(StudentProfile profile) {
        List<StudentCourse> courses = studentCourseRepository.findWithCourseAndDivisionByStudentProfile(profile);

        Map<TermKey, List<StudentCourse>> grouped = new TreeMap<>();
        for (StudentCourse course : courses) {
            TermKey key = toTermKey(profile.getAdmissionYear(), course);
            // 수강년도/학기 정보가 없어 학기를 특정할 수 없는 과목은 플래너에 배치할 수 없어 제외한다.
            if (key == null) {
                continue;
            }
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(course);
        }

        return grouped.entrySet().stream()
                .map(entry -> toCompletedTerm(entry.getKey(), entry.getValue()))
                .toList();
    }

    private TermKey toTermKey(int admissionYear, StudentCourse course) {
        Integer takenYear = course.getTakenYear();
        Semester takenSemester = course.getTakenSemester();
        if (takenYear == null || takenSemester == null) {
            return null;
        }
        int yearLevel = takenYear - admissionYear + 1;
        int semester = (takenSemester == Semester.FIRST || takenSemester == Semester.SUMMER) ? 1 : 2;
        return new TermKey(yearLevel, semester);
    }

    private PlannerResponse.CompletedTerm toCompletedTerm(TermKey key, List<StudentCourse> courses) {
        boolean inProgress = courses.stream().anyMatch(c -> c.getStatus() == CourseStatus.IN_PROGRESS);
        int totalCredit = courses.stream().mapToInt(StudentCourse::getCredit).sum();

        return PlannerResponse.CompletedTerm.builder()
                .yearLevel(key.yearLevel())
                .semester(key.semester())
                .plannerTermVersionId(key.syntheticVersionId())
                .name(key.yearLevel() + "학년 " + key.semester() + "학기")
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
                .courseName(course != null ? course.getName() : studentCourse.getRawCourseName())
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

    private record TermKey(int yearLevel, int semester) implements Comparable<TermKey> {
        long syntheticVersionId() {
            return yearLevel * 10L + semester;
        }

        @Override
        public int compareTo(TermKey other) {
            int byYear = Integer.compare(yearLevel, other.yearLevel);
            return byYear != 0 ? byYear : Integer.compare(semester, other.semester);
        }
    }

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