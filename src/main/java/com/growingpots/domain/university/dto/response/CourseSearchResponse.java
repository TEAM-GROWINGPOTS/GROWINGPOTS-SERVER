package com.growingpots.domain.university.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
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
        // Lombok이 boolean 필드 isEnglish/isSw에 대해 isEnglish()/isSw() 게터를 만드는데, Jackson은
        // 그 게터의 "is" 접두사를 벗겨 "english"/"sw"로 직렬화해버려 스펙과 어긋난다. 게터 자체에
        // @JsonProperty를 얹어 프로퍼티명을 고정한다(필드에 얹으면 게터 쪽 이름과 중복 노출됨).
        @Getter(onMethod_ = @__(@JsonProperty("isEnglish")))
        private final boolean isEnglish;
        @Getter(onMethod_ = @__(@JsonProperty("isSw")))
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
