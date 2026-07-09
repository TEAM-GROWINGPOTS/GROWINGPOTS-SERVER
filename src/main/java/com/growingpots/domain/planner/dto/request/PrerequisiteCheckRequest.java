package com.growingpots.domain.planner.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Schema(description = "선수과목 검사 요청")
public record PrerequisiteCheckRequest(
        @Schema(description = "선수과목 검사 대상 과목 PK 목록") @NotEmpty List<@NotNull Long> courseIds
) {
}