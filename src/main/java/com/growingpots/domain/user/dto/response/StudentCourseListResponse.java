package com.growingpots.domain.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Schema(description = "이수 과목 목록 응답")
@Getter
@Builder
public class StudentCourseListResponse {

    @Schema(description = "이수 과목 목록")
    private final List<CourseInfo> courses;

    @Schema(description = "개별 이수 과목 정보")
    @Getter
    @Builder
    public static class CourseInfo {

        @Schema(description = "이수 과목 PK")
        private final Long studentCourseId;

        @Schema(description = "학수번호", example = "THE2001")
        private final String courseCode;

        @Schema(description = "과목명", example = "연극문헌과연기")
        private final String name;

        @Schema(description = "개설 학과명. 과목 마스터와 매칭된 경우에만 값이 있음. 직접 추가 과목은 null", nullable = true)
        private final String departmentName;

        @Schema(description = "학점", example = "3")
        private final int credit;

        @Schema(description = "적용 이수구분명. 교양 과목인 경우에만 값이 있음. 그 외 null", nullable = true)
        private final String appliedDivisionName;

        @Schema(description = "이수 연도", example = "2023", nullable = true)
        private final Integer takenYear;

        @Schema(description = "이수 학기 (1학기 · 여름학기 · 2학기 · 겨울학기)", example = "1학기", nullable = true)
        private final String takenSemester;
    }
}