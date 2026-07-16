package com.growingpots.domain.university.service;

import com.growingpots.domain.planner.repository.PlannerVersionItemRepository;
import com.growingpots.domain.transcript.entity.enums.CourseStatus;
import com.growingpots.domain.transcript.repository.StudentCourseRepository;
import com.growingpots.domain.university.dto.request.CourseSearchRequest;
import com.growingpots.domain.university.dto.response.CourseSearchResponse;
import com.growingpots.domain.university.entity.Course;
import com.growingpots.domain.university.entity.CrossMajorRecognizedCourse;
import com.growingpots.domain.university.entity.Department;
import com.growingpots.domain.university.entity.Division;
import com.growingpots.domain.university.entity.School;
import com.growingpots.domain.university.entity.enums.CourseDivisionFilter;
import com.growingpots.domain.university.entity.enums.DivisionCategory;
import com.growingpots.domain.university.repository.CourseRepository;
import com.growingpots.domain.university.repository.CrossMajorRecognizedCourseRepository;
import com.growingpots.domain.university.specification.CourseSpecifications;
import com.growingpots.domain.user.entity.StudentProfile;
import com.growingpots.domain.user.repository.StudentProfileRepository;
import com.growingpots.global.exception.BaseException;
import com.growingpots.global.response.error.ErrorCode;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CourseService {

    private final StudentProfileRepository studentProfileRepository;
    private final CourseRepository courseRepository;
    private final CrossMajorRecognizedCourseRepository crossMajorRecognizedCourseRepository;
    private final StudentCourseRepository studentCourseRepository;
    private final PlannerVersionItemRepository plannerVersionItemRepository;

    @Transactional(readOnly = true)
    public CourseSearchResponse searchCourses(Long memberId, CourseSearchRequest request) {
        StudentProfile profile = studentProfileRepository.findWithDetailsByMemberId(memberId)
                .orElseThrow(() -> new BaseException(ErrorCode.STUDENT_PROFILE_NOT_FOUND));
        School school = profile.getSchool();
        Department department = profile.getDepartment();

        List<CourseDivisionFilter> divisionFilters = request.divisionCategory();
        boolean crossMajorRequested = divisionFilters != null && divisionFilters.contains(CourseDivisionFilter.CROSS_MAJOR);
        List<DivisionCategory> categories = divisionFilters == null ? List.of() : divisionFilters.stream()
                .filter(filter -> filter != CourseDivisionFilter.CROSS_MAJOR)
                .map(filter -> DivisionCategory.valueOf(filter.name()))
                .toList();

        Map<Long, Division> recognizedDivisionByCourseId = new HashMap<>();
        List<Long> crossMajorCourseIds = List.of();
        if (crossMajorRequested) {
            List<CrossMajorRecognizedCourse> recognized = crossMajorRecognizedCourseRepository.findByTargetDepartment(department);
            crossMajorCourseIds = recognized.stream().map(r -> r.getCourse().getId()).toList();
            for (CrossMajorRecognizedCourse r : recognized) {
                recognizedDivisionByCourseId.put(r.getCourse().getId(), r.getRecognizedDivision());
            }
        }

        Specification<Course> spec = combine(Arrays.asList(
                CourseSpecifications.withSchool(school),
                CourseSpecifications.withActiveOnly(),
                CourseSpecifications.withKeyword(request.keyword()),
                CourseSpecifications.withCollegeName(request.collegeName()),
                CourseSpecifications.withDepartmentId(request.departmentId()),
                CourseSpecifications.withYears(request.year()),
                CourseSpecifications.withSemesters(request.semester()),
                CourseSpecifications.withCredits(request.credits()),
                CourseSpecifications.withOtherRequired(request.otherRequired()),
                CourseSpecifications.withDivisionFilters(categories, crossMajorRequested, crossMajorCourseIds),
                CourseSpecifications.withFetchedAssociations()
        ));

        Pageable pageable = PageRequest.of(request.pageOrDefault(), request.sizeOrDefault());
        Page<Course> coursePage = courseRepository.findAll(spec, pageable);

        Set<Long> completedCourseIds = new HashSet<>(
                studentCourseRepository.findCourseIdsByStudentProfileAndStatus(profile, CourseStatus.COMPLETED));

        // 학생의 현재 선택된 플래너 버전에 담긴 과목 목록 - "이미 담음" 표시용
        Set<Long> plannerCourseIds = plannerVersionItemRepository.findSelectedByStudentProfile(profile).stream()
                .map(item -> item.getCourse().getId())
                .collect(Collectors.toSet());

        List<CourseSearchResponse.CourseInfo> courseInfos = coursePage.getContent().stream()
                .map(course -> toCourseInfo(course, completedCourseIds, plannerCourseIds, recognizedDivisionByCourseId))
                .toList();

        return CourseSearchResponse.builder()
                .courses(courseInfos)
                .page(CourseSearchResponse.PageInfo.builder()
                        .page(coursePage.getNumber())
                        .size(coursePage.getSize())
                        .totalElements(coursePage.getTotalElements())
                        .hasNext(coursePage.hasNext())
                        .build())
                .build();
    }

    private Specification<Course> combine(List<Specification<Course>> specifications) {
        return specifications.stream()
                .filter(spec -> spec != null)
                .reduce(Specification::and)
                .orElse(null);
    }

    private CourseSearchResponse.CourseInfo toCourseInfo(
            Course course, Set<Long> completedCourseIds, Set<Long> plannerCourseIds,
            Map<Long, Division> recognizedDivisionByCourseId) {
        // recognizedDivisionByCourseId는 요청에 CROSS_MAJOR가 포함된 경우에만 채워진다(searchCourses 참고).
        // 즉 이 과목이 "타전공 인정 대상"으로 조회된 경우에만, 과목 자체의 기본 이수구분보다 인정받은
        // 이수구분(학과마다 다를 수 있음)을 우선해서 보여준다. CROSS_MAJOR 없이 검색하면 이 분기는 타지 않는다.
        Division recognizedDivision = recognizedDivisionByCourseId.get(course.getId());
        DivisionCategory displayedCategory;
        String defaultDivisionName;
        if (recognizedDivision != null) {
            displayedCategory = recognizedDivision.getCategory();
            defaultDivisionName = displayedCategory.getDisplayName();
        } else if (course.getDefaultDivision() != null) {
            displayedCategory = course.getDefaultDivision().getCategory();
            defaultDivisionName = displayedCategory.getDisplayName();
        } else {
            displayedCategory = null;
            defaultDivisionName = null;
        }

        return CourseSearchResponse.CourseInfo.builder()
                .courseId(course.getId())
                .courseCode(course.getCourseCode())
                .name(course.getName())
                .credit(course.getCredit())
                .departmentName(course.getOfferingDepartment() != null ? course.getOfferingDepartment().getName() : null)
                .defaultDivisionName(defaultDivisionName)
                .recommendedYearLow(course.getRecommendedYearLow())
                .recommendedYearHigh(course.getRecommendedYearHigh())
                .openedSemester(course.getOpenedSemester() != null ? course.getOpenedSemester().name() : null)
                .isEnglish(course.isEnglish())
                .isSw(course.isSw())
                .alreadyCompleted(completedCourseIds.contains(course.getId()))
                .inPlanner(plannerCourseIds.contains(course.getId()))
                .area(extractAreaInfo(course, displayedCategory))
                .build();
    }

    // "이수구분별 과목 조회"(GraduationService.extractAreaInfo), 플래너 응답(PlannerService)과 동일 규칙:
    // 화면에 표시 중인 이수구분이 배분이수교과이고 Course에 영역이 매칭돼 있을 때만 채운다.
    private CourseSearchResponse.AreaInfo extractAreaInfo(Course course, DivisionCategory displayedCategory) {
        if (displayedCategory != DivisionCategory.DISTRIBUTED_GE || course.getGeArea() == null) {
            return null;
        }
        return CourseSearchResponse.AreaInfo.builder()
                .code(course.getGeArea().getCode())
                .name(course.getGeArea().getName())
                .build();
    }
}
