package com.growingpots.domain.planner.dto.response;

import java.util.List;

public record PlannerSaveResponse(
        Long plannerSimulationId,
        List<TermResponse> terms
) {
    public record TermResponse(
            Long plannerTermId,
            int yearLevel,
            int semester,
            List<VersionResponse> versions
    ) {
    }

    public record VersionResponse(
            Long plannerTermVersionId,
            int versionNo,
            boolean isSelected,
            List<ItemResponse> items
    ) {
    }

    public record ItemResponse(
            Long plannerVersionItemId,
            Long courseId
    ) {
    }
}