package com.growingpots.domain.user.dto.request;

import com.growingpots.domain.transcript.entity.enums.Semester;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record StudentCourseUpdateRequest(
        @NotEmpty @Valid List<CourseUpdateItem> courses
) {
    // studentCourseId가 null이면 신규 추가(직접 추가한 과목), 있으면 기존 항목 수정.
    // 요청에 없는 기존 studentCourseId는 편집 화면에서 삭제된 것으로 간주해 함께 삭제된다.
    public record CourseUpdateItem(
            Long studentCourseId,
            Long courseId,
            @NotBlank String rawCourseName,
            Long departmentId,
            @NotNull Integer credit,
            Long appliedDivisionId,
            Integer takenYear,
            Semester takenSemester
    ) {
    }
}
