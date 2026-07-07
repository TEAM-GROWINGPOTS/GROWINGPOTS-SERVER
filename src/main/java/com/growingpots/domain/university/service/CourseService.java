package com.growingpots.domain.university.service;

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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

        Specification<Course> spec = combine(List.of(
                CourseSpecifications.withSchool(school),
                CourseSpecifications.withKeyword(request.keyword()),
                CourseSpecifications.withCollegeName(request.collegeName()),
                CourseSpecifications.withDepartmentId(request.departmentId()),
                CourseSpecifications.withYears(request.year()),
                CourseSpecifications.withSemesters(request.semester()),
                CourseSpecifications.withCredits(request.credits()),
                CourseSpecifications.withDivisionFilters(categories, crossMajorRequested, crossMajorCourseIds)
        ));

        Pageable pageable = PageRequest.of(request.pageOrDefault(), request.sizeOrDefault());
        Page<Course> coursePage = courseRepository.findAll(spec, pageable);

        Set<Long> completedCourseIds = new HashSet<>(
                studentCourseRepository.findCourseIdsByStudentProfileAndStatus(profile, CourseStatus.COMPLETED));

        List<CourseSearchResponse.CourseInfo> courseInfos = coursePage.getContent().stream()
                .map(course -> toCourseInfo(course, completedCourseIds, recognizedDivisionByCourseId))
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
            Course course, Set<Long> completedCourseIds, Map<Long, Division> recognizedDivisionByCourseId) {
        // 학생 학과 기준 타전공 인정 이수구분이 있으면 과목 자체의 기본 이수구분보다 우선한다.
        Division recognizedDivision = recognizedDivisionByCourseId.get(course.getId());
        String defaultDivisionName;
        if (recognizedDivision != null) {
            defaultDivisionName = recognizedDivision.getCategory().getDisplayName();
        } else if (course.getDefaultDivision() != null) {
            defaultDivisionName = course.getDefaultDivision().getCategory().getDisplayName();
        } else {
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
                .inPlanner(false)
                .build();
    }
}
