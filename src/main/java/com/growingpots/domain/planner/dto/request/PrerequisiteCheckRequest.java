package com.growingpots.domain.planner.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record PrerequisiteCheckRequest(
        @NotEmpty List<@NotNull Long> courseIds
) {
}