package com.growingpots.domain.graduation.dto.response;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GraduationCourseResponse {

    private final String divisionCode;
    private final String divisionName;
    private final List<MajorCourses> majors;

    @Getter
    @Builder
    public static class MajorCourses {
        private final String majorType;
        private final String departmentName;
        private final int current;
        private final Integer required;
        private final boolean satisfied;
        private final boolean hasRequiredList;
        private final List<CourseInfo> courses;
    }

    @Getter
    @Builder
    public static class CourseInfo {
        private final Long studentCourseId;
        private final String name;
        private final String departmentName;
        private final int credit;
        // 이수: takenYear(String), 미이수: COURSE.recommendedYear("1-2" 등)
        private final String grade;
        // 이수: takenSemester + "학기", 미이수: openedSemester 표시용 문자열(FIRST→"1학기" 등)
        private final String semester;
        private final boolean taken;
    }
}