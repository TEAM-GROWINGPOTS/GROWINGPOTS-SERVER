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
            description = "PDF 스냅샷 기반 졸업 현황을 반환한다. "
                    + "majorType=ALL: 본전공/복수전공/교양/기타 4개 섹션 분리(sections 필드, conditions=null). "
                    + "majorType=PRIMARY: 본전공 MAJOR_* 조건 + 전공 이수구분 영어/SW. "
                    + "majorType=MULTI: 복수전공 MAJOR_* 조건 + 전공 이수구분 영어/SW. "
                    + "majorType=GE: REQUIRED_GE/DISTRIBUTED_GE/FREE_GE + 교양 이수구분 영어/SW. "
                    + "majorType=OTHERS: GENERAL_ELECTIVE(기타) 조건 하나만 반환, 영어/SW 미포함. "
                    + "영어/SW 강의는 appliedDivision.category 기준으로 전공·교양 탭에만 배치된다. "
                    + "source=PLANNED는 선택된 플래너 버전의 계획 과목 중 미이수/미수강 과목을 스냅샷에 합산한 예상 졸업현황을 반환한다. "
                    + "플래너가 없거나 신규 계획 과목이 없으면 COMPLETED와 동일한 응답. "
                    + "이수구분 미지정 계획 과목은 기타(GENERAL_ELECTIVE)로 처리된다."
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
                    + "majorType=ALL: 보유 전공 전부. PRIMARY: 본전공. MULTI: 복수전공. GE/OTHERS: 본전공 스냅샷 기준. "
                    + "ENGLISH_COURSE/SW_CERT_COURSE 요청 시 majorType에 따라 해당 이수구분 과목만 반환된다."
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