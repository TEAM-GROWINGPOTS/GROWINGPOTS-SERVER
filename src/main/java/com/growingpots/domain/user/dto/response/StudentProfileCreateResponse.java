package com.growingpots.domain.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Schema(description = "학생 프로필 생성 응답")
@Getter
@Builder
public class StudentProfileCreateResponse {

    @Schema(description = "생성된 학생 프로필 PK")
    private final Long studentProfileId;

    @Schema(description = "본전공 정보")
    private final MainMajorInfo mainMajor;

    @Schema(description = "본전공 정보")
    @Getter
    @Builder
    public static class MainMajorInfo {

        @Schema(description = "학생 전공 PK")
        private final Long studentMajorId;

        @Schema(description = "학과명", example = "컴퓨터공학과")
        private final String departmentName;
    }
}