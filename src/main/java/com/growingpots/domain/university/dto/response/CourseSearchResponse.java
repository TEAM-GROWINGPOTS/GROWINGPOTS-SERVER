package com.growingpots.domain.university.dto.response;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CourseSearchResponse {

    private final List<CourseInfo> courses;
    private final PageInfo page;

    @Getter
    @Builder
    public static class CourseInfo {
        private final Long courseId;
        private final String courseCode;
        private final String name;
        private final int credit;
        private final String departmentName;
        private final String defaultDivisionName;
        private final Integer recommendedYearLow;
        private final Integer recommendedYearHigh;
        private final String openedSemester;
        private final boolean isEnglish;
        private final boolean isSw;
        private final boolean alreadyCompleted;
        private final boolean inPlanner;
    }

    @Getter
    @Builder
    public static class PageInfo {
        private final int page;
        private final int size;
        private final long totalElements;
        private final boolean hasNext;
    }
}
