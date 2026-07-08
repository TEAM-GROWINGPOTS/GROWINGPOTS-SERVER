package com.growingpots.domain.planner.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PlannerResponse {

    private final List<CompletedTerm> completedTerms;
    private final List<PlannedTerm> plannedTerms;

    @Getter
    @Builder
    public static class CompletedTerm {
        private final int yearLevel;
        private final int semester;
        private final Long plannerTermVersionId;
        private final String name;
        private final String status;
        private final int totalCredit;
        // 조회전용이라 항상 true. 프론트가 completedTerms/plannedTerms를 하나의 카드 리스트로
        // 합쳐서 다룰 때, 배열 출처를 안 따지고 이 값 하나로 편집 아이콘(⋮/⇄) 노출 여부를 정할 수 있게.
        private final boolean locked;
        private final List<CompletedCourse> courses;
    }

    @Getter
    @Builder
    public static class CompletedCourse {
        private final Long studentCourseId;
        private final Long courseId;
        private final String courseName;
        private final String departmentName;
        private final String divisionCategory;
        private final String divisionName;
        private final Integer recommendedYearLow;
        private final Integer recommendedYearHigh;
        private final String openedSemester;
        private final int credit;
    }

    @Getter
    @Builder
    public static class PlannedTerm {
        private final Long plannerTermId;
        private final int yearLevel;
        private final int semester;
        private final int termOrder;
        private final boolean locked;
        private final List<Version> versions;
    }

    @Getter
    @Builder
    public static class Version {
        private final Long plannerTermVersionId;
        private final int versionNo;
        private final String name;
        // Lombok이 boolean isSelected에 대해 isSelected() 게터를 만드는데, Jackson은 "is" 접두사를
        // 벗겨 "selected"로 직렬화해버려 스펙과 어긋난다(CourseSearchResponse.isEnglish/isSw와 동일 이슈).
        @Getter(onMethod_ = @__(@JsonProperty("isSelected")))
        private final boolean isSelected;
        private final int totalCredit;
        private final List<PlannedCourse> courses;
    }

    @Getter
    @Builder
    public static class PlannedCourse {
        private final Long plannerVersionItemId;
        private final Long courseId;
        private final String courseName;
        private final String departmentName;
        // 타전공인정과목의 인정 이수구분이 없으면(또는 과목 자체에 기본 이수구분이 없으면) null일 수 있다.
        private final String divisionCategory;
        private final String divisionName;
        private final Integer recommendedYearLow;
        private final Integer recommendedYearHigh;
        private final String openedSemester;
        private final int credit;
        private final int positionOrder;
    }
}
