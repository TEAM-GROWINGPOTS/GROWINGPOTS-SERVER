package com.growingpots.domain.university.dto.request;

import com.growingpots.domain.university.entity.enums.CourseDivisionFilter;
import com.growingpots.domain.university.entity.enums.CourseOtherRequiredFilter;
import com.growingpots.domain.university.entity.enums.OpenedSemester;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;

// 같은 필드 안의 값끼리는 OR(합집합), 서로 다른 필드끼리는 AND(교집합)로 결합한다.
public record CourseSearchRequest(
        @Schema(description = "과목명 또는 학수번호 부분 일치 검색어") String keyword,
        @Schema(description = "단과대학명 필터") String collegeName,
        @Schema(description = "학과 PK 필터") Long departmentId,
        @Schema(description = "이수구분 필터 (다중 선택, OR 결합). "
                + "CROSS_MAJOR: 학생 학과 기준 타전공인정과목 조회 (DIVISION 카테고리 아님)")
        List<CourseDivisionFilter> divisionCategory,
        @Schema(description = "권장 학년 필터 (다중 선택, OR 결합)", example = "[2, 3]") List<Integer> year,
        @Schema(description = "개설 학기 필터 (다중 선택, OR 결합)")
        List<OpenedSemester> semester,
        @Schema(description = "학점 필터 (다중 선택, OR 결합). 4이면 4학점 이상(≥4)으로 처리", example = "[2, 3]") List<Integer> credits,
        @Schema(description = "기타 필수 필터 (다중 선택, OR 결합)")
        List<CourseOtherRequiredFilter> otherRequired,
        @Schema(description = "캠퍼스 필터") String campus,
        @Schema(description = "페이지 번호 (0-based, 기본값 0)", example = "0") @Min(0) Integer page,
        @Schema(description = "페이지 크기 (기본값 20, 최대 100)", example = "20") @Min(1) @Max(100) Integer size
) {
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;

    public int pageOrDefault() {
        return page != null ? page : DEFAULT_PAGE;
    }

    public int sizeOrDefault() {
        return size != null ? size : DEFAULT_SIZE;
    }
}