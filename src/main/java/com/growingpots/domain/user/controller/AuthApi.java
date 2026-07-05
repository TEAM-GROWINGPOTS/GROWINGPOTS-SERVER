package com.growingpots.domain.user.controller;

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
@Tag(name = "Auth", description = "소셜 로그인 관련 API")
public @interface AuthApi {

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @Operation(
            summary = "소셜 로그인",
            description = "프론트에서 소셜 SDK로 발급받은 access token으로 로그인/회원가입을 처리하고 서비스 JWT를 발급한다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그인 성공 (AUTH_200)"),
            @ApiResponse(responseCode = "400", description = "요청값 검증 실패(CMN_002) 또는 지원하지 않는 소셜 로그인(AUTH_003)"),
            @ApiResponse(responseCode = "401", description = "유효하지 않은 소셜 access token(AUTH_001)"),
            @ApiResponse(responseCode = "502", description = "소셜 서버 통신 실패(AUTH_004)")
    })
    @interface Login {
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @Operation(
            summary = "토큰 재발급",
            description = "refreshToken으로 accessToken과 refreshToken을 함께 재발급한다. accessToken 만료 시 자동 호출된다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "재발급 성공 (AUTH_200_2)"),
            @ApiResponse(responseCode = "400", description = "요청값 검증 실패(CMN_002)"),
            @ApiResponse(responseCode = "401",
                    description = "만료된 refreshToken(AUTH_002), 유효하지 않은 refreshToken(AUTH_001), "
                            + "저장된 토큰과 불일치(AUTH_005)")
    })
    @interface Reissue {
    }
}
