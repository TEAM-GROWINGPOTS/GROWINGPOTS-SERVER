package com.growingpots.domain.planner.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Tag(name = "Planner", description = "학기 플래너 API")
public @interface PlannerApi {

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @Operation(
            summary = "학기 플래너 저장",
            description = "학기 플래너를 full-replace 방식으로 저장한다. "
                    + "plannerSimulationId가 null이면 새 플래너를 생성하고, 값이 있으면 기존 플래너를 전체 교체한다. "
                    + "각 학기(term)에는 정확히 1개의 isSelected=true 버전이 있어야 한다. "
                    + "1학기 개설 과목은 1학기 term에만, 2학기 개설 과목은 2학기 term에만 추가할 수 있다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "저장 성공 (PLAN_200_1)"),
            @ApiResponse(responseCode = "400", description = "데이터 정합성 오류 (PLAN_004)"),
            @ApiResponse(responseCode = "401", description = "인증 실패 (CMN_005)"),
            @ApiResponse(responseCode = "403", description = "플래너 접근 권한 없음 (PLAN_003)"),
            @ApiResponse(responseCode = "404", description = "학적 정보 없음 (USER_003) / 플래너 없음 (PLAN_002) / 과목 없음 (PLAN_001)")
    })
    @interface SavePlanner {
    }
}