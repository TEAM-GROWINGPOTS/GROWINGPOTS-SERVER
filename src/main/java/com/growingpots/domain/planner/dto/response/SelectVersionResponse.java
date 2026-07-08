package com.growingpots.domain.planner.dto.response;

public record SelectVersionResponse(
        Long plannerTermId,
        Long selectedVersionId
) {
}