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
                    + "majorType=OTHERS: GENERAL_ELECTIVE + 기타 이수구분 영어/SW. "
                    + "영어/SW 강의는 appliedDivision.category 기준으로 해당 탭에 배치된다. "
                    + "graduationRequired: 해당 학과에 이수구분과 무관한 독립 졸업요건(예: 스포츠의학과 졸업필수)이 "
                    + "있는 경우에만 채워짐(PRIMARY/MULTI 및 ALL의 sections.primary/multi). 그 외엔 null. "
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
                    + "majorType=ALL: 보유 전공 전부. PRIMARY: 본전공. MULTI: 복수전공. GE/OTHERS: 본전공 스냅샷 기준. "
                    + "ENGLISH_COURSE/SW_CERT_COURSE 요청 시 majorType에 따라 해당 이수구분 과목만 반환된다. "
                    + "GRADUATION_REQUIRED: 학과 자체의 독립 졸업요건(이수구분 무관, 예: 스포츠의학과 졸업필수) "
                    + "하위조건에 연결된 과목들을 이수/미이수 하나의 리스트로 합쳐 반환. current/required는 "
                    + "\"만족한 조건 수/전체 조건 수\"이고, unmetDescriptions에 미충족 조건별 상세 문구가 담긴다. "
                    + "해당 학과가 아니면 결과가 비어 있다(hasRequiredList=false)."
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