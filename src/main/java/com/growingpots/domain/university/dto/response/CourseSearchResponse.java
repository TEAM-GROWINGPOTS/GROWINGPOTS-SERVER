package com.growingpots.domain.university.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Schema(description = "과목 검색 응답")
@Getter
@Builder
public class CourseSearchResponse {

    @Schema(description = "과목 목록")
    private final List<CourseInfo> courses;

    @Schema(description = "페이지 정보")
    private final PageInfo page;

    @Schema(description = "개별 과목 정보")
    @Getter
    @Builder
    public static class CourseInfo {

        @Schema(description = "과목 PK")
        private final Long courseId;

        @Schema(description = "학수번호", example = "CHE331")
        private final String courseCode;

        @Schema(description = "과목명", example = "화공열역학1")
        private final String name;

        @Schema(description = "학점", example = "3")
        private final int credit;

        @Schema(description = "개설 학과명", example = "화학공학과")
        private final String departmentName;

        @Schema(description = "기본 이수구분명. CROSS_MAJOR 조회 시 학생 학과 기준 인정 이수구분명으로 표시", example = "전공필수")
        private final String defaultDivisionName;

        @Schema(description = "권장 학년 하한. 없으면 null", nullable = true, example = "3")
        private final Integer recommendedYearLow;

        @Schema(description = "권장 학년 상한. recommendedYearLow와 다르면 범위(예: 1~2학년). 없으면 null", nullable = true, example = "3")
        private final Integer recommendedYearHigh;

        @Schema(description = "개설 학기", allowableValues = {"FIRST", "SECOND", "BOTH"})
        private final String openedSemester;

        // Lombok이 boolean 필드 isEnglish/isSw에 대해 isEnglish()/isSw() 게터를 만드는데, Jackson은
        // 그 게터의 "is" 접두사를 벗겨 "english"/"sw"로 직렬화해버려 스펙과 어긋난다. 게터 자체에
        // @JsonProperty를 얹어 프로퍼티명을 고정한다(필드에 얹으면 게터 쪽 이름과 중복 노출됨).
        @Schema(description = "영어 강의 여부")
        @Getter(onMethod_ = @__(@JsonProperty("isEnglish")))
        private final boolean isEnglish;

        @Schema(description = "SW 인증 강의 여부")
        @Getter(onMethod_ = @__(@JsonProperty("isSw")))
        private final boolean isSw;

        @Schema(description = "이미 이수 완료한 과목 여부")
        private final boolean alreadyCompleted;

        @Schema(description = "플래너에 담긴 과목 여부. 현재 항상 false")
        private final boolean inPlanner;
    }

    @Schema(description = "페이지 정보")
    @Getter
    @Builder
    public static class PageInfo {

        @Schema(description = "현재 페이지 (0-based)", example = "0")
        private final int page;

        @Schema(description = "페이지 크기", example = "20")
        private final int size;

        @Schema(description = "전체 과목 수", example = "137")
        private final long totalElements;

        @Schema(description = "다음 페이지 존재 여부")
        private final boolean hasNext;
    }
}