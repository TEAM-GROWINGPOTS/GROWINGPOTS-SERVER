package com.growingpots.domain.transcript.controller;

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
@Tag(name = "Transcript", description = "졸업사정관리표 PDF 업로드/분석 관련 API")
public @interface TranscriptApi {

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @Operation(
            summary = "졸업사정관리표 PDF 업로드",
            description = "졸업사정관리표 PDF를 업로드해 이수 과목, 전공별 졸업요건 분석 결과, 비학점 인증 결과를 파싱해 저장한다. "
                    + "이미 분석 결과가 있으면 PDF 기반 데이터만 새로 파싱된 값으로 대체한다(직접 추가/수정한 데이터는 보존)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "PDF 분석 완료 (TRANS_201)"),
            @ApiResponse(responseCode = "400", description = "지원하지 않는 PDF 형식(TRANS_002)"),
            @ApiResponse(responseCode = "401", description = "인증 실패(CMN_005)"),
            @ApiResponse(responseCode = "413", description = "PDF 용량 초과, 10MB 제한(TRANS_004)"),
            @ApiResponse(responseCode = "500", description = "PDF 파싱 실패(TRANS_001)")
    })
    @interface UploadTranscript {
    }
}
