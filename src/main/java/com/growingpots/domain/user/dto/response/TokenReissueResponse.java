package com.growingpots.domain.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record TokenReissueResponse(
        @Schema(description = "재발급된 서비스 accessToken") String accessToken
) {
}