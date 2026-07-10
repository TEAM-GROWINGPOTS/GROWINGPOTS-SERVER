package com.growingpots.domain.planner.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "플래너 저장 응답")
public record PlannerSaveResponse(
        @Schema(description = "플래너 시뮬레이션 PK") Long plannerSimulationId,
        @Schema(description = "저장된 학기 목록") List<TermResponse> terms
) {
    @Schema(description = "저장된 학기 응답")
    public record TermResponse(
            @Schema(description = "플래너 학기 PK") Long plannerTermId,
            @Schema(description = "학년", example = "1") int yearLevel,
            @Schema(description = "학기 (1 또는 2)", example = "1") int semester,
            @Schema(description = "버전(폴더) 목록") List<VersionResponse> versions
    ) {
    }

    @Schema(description = "저장된 버전(폴더) 응답")
    public record VersionResponse(
            @Schema(description = "플래너 학기 버전 PK") Long plannerTermVersionId,
            @Schema(description = "버전 번호 (1부터 시작)", example = "1") int versionNo,
            @Schema(description = "선택된 버전 여부") boolean isSelected,
            @Schema(description = "버전 순서 (0-based)") int versionOrder,
            @Schema(description = "버전에 포함된 과목 항목 목록") List<ItemResponse> items
    ) {
    }

    @Schema(description = "저장된 과목 항목 응답")
    public record ItemResponse(
            @Schema(description = "플래너 버전 항목 PK") Long plannerVersionItemId,
            @Schema(description = "과목 PK") Long courseId,
            @Schema(description = "과목 카드 순서 (0-based)") int coursePositionOrder
    ) {
    }
}