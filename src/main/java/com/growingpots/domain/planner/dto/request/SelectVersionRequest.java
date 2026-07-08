package com.growingpots.domain.planner.dto.request;

import jakarta.validation.constraints.NotNull;

public record SelectVersionRequest(
        @NotNull Long plannerTermVersionId
) {
}