package com.growingpots.domain.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "학생 프로필 생성 요청")
public record StudentProfileCreateRequest(
        @Schema(description = "학교 PK") @NotNull Long schoolId,
        @Schema(description = "학과 PK (본전공)") @NotNull Long departmentId,
        @Schema(description = "입학연도", example = "2023") @NotNull Integer admissionYear
) {
}