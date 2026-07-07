package com.growingpots.domain.graduation.controller;

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
@Tag(name = "Graduation", description = "졸업 현황 조회 API")
public @interface GraduationApi {

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @Operation(
            summary = "졸업 현황 조회",
            description = "PDF 스냅샷 기반 졸업 현황(요약 / 조건 9개 / 비학점 인증)을 반환한다. "
                    + "majorType=ALL이면 본전공 기준에 복수전공 전공 학점을 합산. PRIMARY/MULTI는 해당 스냅샷 단건 반환. "
                    + "source=PLANNED는 미구현(추후 플래너 연동)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "졸업 현황 조회 성공 (REQ_200_1)"),
            @ApiResponse(responseCode = "401", description = "인증 실패 (CMN_005)"),
            @ApiResponse(responseCode = "404", description = "학적 정보 없음(USER_003) / 졸업 분석 데이터 없음(REQ_001)")
    })
    @interface GetGraduationStatus {
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @Operation(
            summary = "이수구분별 과목 조회",
            description = "divisionCode(예: MAJOR_REQUIRED)에 해당하는 이수/미이수 과목 목록을 반환한다. "
                    + "hasRequiredList=true인 경우 미이수 필수과목이 포함된다. "
                    + "majorType=ALL이면 보유 전공 전부, PRIMARY=본전공, MULTI=복수전공."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공 (REQ_200_2)"),
            @ApiResponse(responseCode = "400", description = "잘못된 divisionCode (CMN_002)"),
            @ApiResponse(responseCode = "401", description = "인증 실패 (CMN_005)"),
            @ApiResponse(responseCode = "404", description = "학적 정보 없음(USER_003) / 졸업 분석 데이터 없음(REQ_001) / 복수전공 없음(REQ_002)")
    })
    @interface GetDivisionCourses {
    }
}