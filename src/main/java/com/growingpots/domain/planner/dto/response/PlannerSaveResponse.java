package com.growingpots.domain.planner.dto.response;

import com.growingpots.domain.graduation.dto.response.GraduationResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Schema(description = "플래너 저장 응답")
@Getter
@Builder
public class PlannerSaveResponse {

    @Schema(description = "졸업 현황 (PLANNED 모드). 졸업현황 계산 실패 시 null", nullable = true)
    private final GraduationResponse graduation;

    @Schema(description = "이번 저장에서 새로 추가된 과목 중 이미 이수완료/수강중인 과목이 있으면 true")
    private final boolean hasDuplicateCourse;
}