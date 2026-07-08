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

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @Operation(
            summary = "선수과목 검사",
            description = "요청한 courseId 목록에 대해 미이수 선수과목을 반환한다. "
                    + "COMPLETED/IN_PROGRESS 과목은 이수한 것으로 간주한다. "
                    + "학과 특정 선수과목 규칙이 공통(null) 규칙보다 우선 적용된다. "
                    + "선수과목이 없거나 모두 이수한 과목은 결과에 포함되지 않는다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "검사 성공 (PLAN_200_2)"),
            @ApiResponse(responseCode = "400", description = "요청 형식 오류 (CMN_002)"),
            @ApiResponse(responseCode = "401", description = "인증 실패 (CMN_005)"),
            @ApiResponse(responseCode = "404", description = "학적 정보 없음 (USER_003)")
    })
    @interface CheckPrerequisites {
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @Operation(
            summary = "선택 버전(폴더) 변경",
            description = "지정한 학기(plannerTermId)의 선택 버전을 변경한다. "
                    + "한 트랜잭션 안에서 해당 학기의 모든 버전 isSelected=false → 지정 버전만 true로 전환한다. "
                    + "이미 선택된 버전을 다시 지정해도 200을 반환한다(멱등). "
                    + "다른 학기에 속한 versionId를 지정하면 404를 반환한다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "버전 변경 성공 (PLAN_200_4)"),
            @ApiResponse(responseCode = "400", description = "요청 형식 오류 (CMN_002) / 이수 완료 학기 (PLAN_006)"),
            @ApiResponse(responseCode = "401", description = "인증 실패 (CMN_005)"),
            @ApiResponse(responseCode = "404", description = "학적 정보 없음 (USER_003) / 학기·버전 없음 또는 소유 아님 (PLAN_005)")
    })
    @interface SelectVersion {
    }
}