package com.growingpots.domain.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record TokenReissueRequest(
        @Schema(description = "재발급에 사용할 refreshToken")
        @NotBlank String refreshToken
) {
}
