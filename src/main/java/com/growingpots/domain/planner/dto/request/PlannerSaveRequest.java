package com.growingpots.domain.planner.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Schema(description = "플래너 저장 요청 (full-replace)")
public record PlannerSaveRequest(
        @Schema(description = "플래너 시뮬레이션 PK. null이면 새 플래너 생성, 값이 있으면 기존 플래너 전체 교체", nullable = true)
        Long plannerSimulationId,
        @Schema(description = "학기 목록") @NotEmpty @Valid List<TermRequest> terms
) {
    @Schema(description = "학기 요청")
    public record TermRequest(
            @Schema(description = "학년 (1 이상)", example = "1") @NotNull @Min(1) Integer yearLevel,
            @Schema(description = "학기 (1 또는 2)", example = "1") @NotNull @Min(1) @Max(2) Integer semester,
            @Schema(description = "전체 플래너 내 학기 순서 (1부터 시작)", example = "1") @NotNull @Min(1) Integer termOrder,
            @Schema(description = "버전(폴더) 목록. 정확히 1개의 isSelected=true 버전 필요") @NotEmpty @Valid List<VersionRequest> versions
    ) {
    }

    @Schema(description = "버전(폴더) 요청")
    public record VersionRequest(
            @Schema(description = "버전 번호 (1부터 시작)", example = "1") @NotNull Integer versionNo,
            @Schema(description = "버전(폴더) 이름", example = "폴더 1") String name,
            @Schema(description = "선택 여부. 학기당 정확히 1개만 true") @NotNull Boolean isSelected,
            @Schema(description = "과목 목록") List<@Valid ItemRequest> items
    ) {
    }

    @Schema(description = "과목 항목 요청")
    public record ItemRequest(
            @Schema(description = "과목 PK") @NotNull Long courseId,
            @Schema(description = "카드뷰 내 과목 순서 (0-based)", example = "0") @NotNull @Min(0) Integer positionOrder
    ) {
    }
}