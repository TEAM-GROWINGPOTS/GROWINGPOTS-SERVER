package com.growingpots.domain.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record TokenReissueResponse(
        @Schema(description = "새로 발급된 accessToken. JS 메모리에 보관하고 Authorization: Bearer 헤더로 보낸다") String accessToken
) {
}
