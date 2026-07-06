package com.growingpots.domain.user.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class StudentCourseListResponse {

    private final List<CourseInfo> courses;

    @Getter
    @Builder
    public static class CourseInfo {
        private final Long studentCourseId;
        private final String courseCode;
        private final String name;
        private final String departmentName;
        private final int credit;
        private final String appliedDivisionName;
        private final Integer takenYear;
        private final String takenSemester;
    }
}
