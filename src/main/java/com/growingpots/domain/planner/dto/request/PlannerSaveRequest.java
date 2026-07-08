package com.growingpots.domain.planner.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record PlannerSaveRequest(
        Long plannerSimulationId,
        @NotEmpty @Valid List<TermRequest> terms
) {
    public record TermRequest(
            @NotNull @Min(1) Integer yearLevel,
            @NotNull @Min(1) @Max(2) Integer semester,
            @NotNull @Min(1) Integer termOrder,
            @NotEmpty @Valid List<VersionRequest> versions
    ) {
    }

    public record VersionRequest(
            @NotNull Integer versionNo,
            String name,
            @NotNull Boolean isSelected,
            List<@Valid ItemRequest> items
    ) {
    }

    public record ItemRequest(
            @NotNull Long courseId,
            @NotNull @Min(0) Integer positionOrder
    ) {
    }
}