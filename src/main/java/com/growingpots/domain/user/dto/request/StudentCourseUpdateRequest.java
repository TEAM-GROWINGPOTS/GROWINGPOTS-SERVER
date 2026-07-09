package com.growingpots.domain.user.dto.request;

import com.growingpots.domain.transcript.entity.enums.Semester;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Schema(description = "이수 과목 수정 요청")
public record StudentCourseUpdateRequest(
        @Schema(description = "과목 목록 (요청에 없는 기존 과목은 삭제됨)") @NotEmpty @Valid List<CourseUpdateItem> courses
) {
    // studentCourseId가 null이면 신규 추가(직접 추가한 과목), 있으면 기존 항목 수정.
    // 요청에 없는 기존 studentCourseId는 편집 화면에서 삭제된 것으로 간주해 함께 삭제된다.
    @Schema(description = "개별 과목 수정 항목")
    public record CourseUpdateItem(
            @Schema(description = "이수 과목 PK. null이면 직접 추가한 신규 과목으로 처리", nullable = true) Long studentCourseId,
            @Schema(description = "과목 마스터 PK. 직접 추가 과목은 null", nullable = true) Long courseId,
            @Schema(description = "과목명 (PDF 원문 또는 직접 입력)") @NotBlank String rawCourseName,
            @Schema(description = "개설 학과 PK. 없으면 null", nullable = true) Long departmentId,
            @Schema(description = "학점") @NotNull Integer credit,
            @Schema(description = "적용 이수구분 PK. 없으면 null", nullable = true) Long appliedDivisionId,
            @Schema(description = "이수 연도", example = "2023", nullable = true) Integer takenYear,
            @Schema(description = "이수 학기", allowableValues = {"FIRST", "SUMMER", "SECOND", "WINTER"}, nullable = true)
            Semester takenSemester
    ) {
    }
}
