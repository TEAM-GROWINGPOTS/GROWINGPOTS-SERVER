package com.growingpots.domain.user.dto.request;

import jakarta.validation.constraints.NotNull;

public record StudentProfileCreateRequest(
        @NotNull Long schoolId,
        @NotNull Long departmentId,
        @NotNull Integer admissionYear
) {
}
