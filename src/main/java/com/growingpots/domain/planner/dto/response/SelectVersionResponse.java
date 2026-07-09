package com.growingpots.domain.planner.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "선택 버전 변경 응답")
public record SelectVersionResponse(
        @Schema(description = "변경된 플래너 학기 PK") Long plannerTermId,
        @Schema(description = "선택된 버전 PK") Long selectedVersionId
) {
}