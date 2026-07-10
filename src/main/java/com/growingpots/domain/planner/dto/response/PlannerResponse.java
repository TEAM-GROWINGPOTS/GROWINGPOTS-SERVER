package com.growingpots.domain.planner.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Schema(description = "플래너 전체 조회 응답")
@Getter
@Builder
public class PlannerResponse {

    @Schema(description = "이수완료·이수중 학기 목록 (조회전용, 편집 불가)")
    private final List<CompletedTerm> completedTerms;

    @Schema(description = "계획 학기 목록 (편집 대상)")
    private final List<PlannedTerm> plannedTerms;

    @Schema(description = "이수완료·이수중 학기")
    @Getter
    @Builder
    public static class CompletedTerm {

        @Schema(description = "학년", example = "1")
        private final int yearLevel;

        @Schema(description = "학기 (1 또는 2)", example = "1")
        private final int semester;

        @Schema(description = "합성 버전 ID (항상 음수, 다른 API에 전달 불가)")
        private final Long plannerTermVersionId;

        @Schema(description = "학기 표시명", example = "1학년 1학기")
        private final String name;

        @Schema(description = "학기 상태", allowableValues = {"COMPLETED", "IN_PROGRESS"})
        private final String status;

        @Schema(description = "총 이수 학점")
        private final int totalCredit;

        @Schema(description = "이수 과목 목록")
        private final List<CompletedCourse> courses;
    }

    @Schema(description = "이수완료·이수중 과목 정보")
    @Getter
    @Builder
    public static class CompletedCourse {

        @Schema(description = "이수 과목 PK")
        private final Long studentCourseId;

        @Schema(description = "과목 PK")
        private final Long courseId;

        @Schema(description = "과목명", example = "미디어와사회")
        private final String courseName;

        @Schema(description = "개설 학과명", example = "미디어학과")
        private final String departmentName;

        @Schema(description = "이수구분 코드",
                allowableValues = {"MAJOR_BASIC", "MAJOR_REQUIRED", "MAJOR_ELECTIVE",
                        "REQUIRED_GE", "DISTRIBUTED_GE", "FREE_GE", "GENERAL_ELECTIVE"})
        private final String divisionCategory;

        @Schema(description = "이수구분 표시명", example = "전공필수")
        private final String divisionName;

        @Schema(description = "권장 학년 하한. 없으면 null", nullable = true)
        private final Integer recommendedYearLow;

        @Schema(description = "권장 학년 상한. 없으면 null", nullable = true)
        private final Integer recommendedYearHigh;

        @Schema(description = "개설 학기", allowableValues = {"FIRST", "SECOND", "BOTH"})
        private final String openedSemester;

        @Schema(description = "학점", example = "3")
        private final int credit;
    }

    @Schema(description = "계획 학기")
    @Getter
    @Builder
    public static class PlannedTerm {

        @Schema(description = "플래너 학기 PK")
        private final Long plannerTermId;

        @Schema(description = "학년", example = "2")
        private final int yearLevel;

        @Schema(description = "학기 (1 또는 2)", example = "1")
        private final int semester;

        @Schema(description = "버전(폴더) 목록")
        private final List<Version> versions;
    }

    @Schema(description = "플래너 버전(폴더)")
    @Getter
    @Builder
    public static class Version {

        @Schema(description = "플래너 학기 버전 PK")
        private final Long plannerTermVersionId;

        @Schema(description = "버전 번호 (1부터 시작)", example = "1")
        private final int versionNo;

        @Schema(description = "버전(폴더) 이름", example = "폴더 1")
        private final String name;

        @Schema(description = "버전(폴더) 표시 순서 (0-based)")
        private final int versionOrder;

        // Lombok이 boolean isSelected에 대해 isSelected() 게터를 만드는데, Jackson은 "is" 접두사를
        // 벗겨 "selected"로 직렬화해버려 스펙과 어긋난다(CourseSearchResponse.isEnglish/isSw와 동일 이슈).
        @Schema(description = "노드뷰에 연결된 선택 버전 여부")
        @Getter(onMethod_ = @__(@JsonProperty("isSelected")))
        private final boolean isSelected;

        @Schema(description = "이 버전에 담긴 과목의 총 학점")
        private final int totalCredit;

        @Schema(description = "계획 과목 목록")
        private final List<PlannedCourse> courses;
    }

    @Schema(description = "계획 과목 정보")
    @Getter
    @Builder
    public static class PlannedCourse {

        @Schema(description = "플래너 버전 항목 PK")
        private final Long plannerVersionItemId;

        @Schema(description = "과목 PK")
        private final Long courseId;

        @Schema(description = "과목명", example = "경영정보시스템")
        private final String courseName;

        @Schema(description = "개설 학과명", example = "산업경영공학과")
        private final String departmentName;

        // 타전공인정과목의 인정 이수구분이 없으면(또는 과목 자체에 기본 이수구분이 없으면) null일 수 있다.
        @Schema(description = "이수구분 코드. 타전공인정과목의 인정 이수구분이 없거나 기본 이수구분이 없으면 null",
                allowableValues = {"MAJOR_BASIC", "MAJOR_REQUIRED", "MAJOR_ELECTIVE",
                        "REQUIRED_GE", "DISTRIBUTED_GE", "FREE_GE", "GENERAL_ELECTIVE"},
                nullable = true)
        private final String divisionCategory;

        @Schema(description = "이수구분 표시명. divisionCategory가 null이면 null", nullable = true, example = "전공필수")
        private final String divisionName;

        @Schema(description = "권장 학년 하한. 없으면 null", nullable = true)
        private final Integer recommendedYearLow;

        @Schema(description = "권장 학년 상한. 없으면 null", nullable = true)
        private final Integer recommendedYearHigh;

        @Schema(description = "개설 학기", allowableValues = {"FIRST", "SECOND", "BOTH"})
        private final String openedSemester;

        @Schema(description = "학점", example = "3")
        private final int credit;

        @Schema(description = "카드뷰 내 과목 순서 (0-based)")
        private final int coursePositionOrder;
    }
}