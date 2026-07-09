package com.growingpots.domain.planner.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "선택 버전 변경 요청")
public record SelectVersionRequest(
        @Schema(description = "선택할 버전 PK") @NotNull Long plannerTermVersionId
) {
}