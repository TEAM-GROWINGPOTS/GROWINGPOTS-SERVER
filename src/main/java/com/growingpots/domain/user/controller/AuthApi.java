package com.growingpots.domain.user.controller;

import com.growingpots.domain.user.dto.response.OAuthLoginResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
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
            description = """
                    프론트에서 소셜 SDK로 발급받은 access token으로 로그인/회원가입을 처리하고 서비스 JWT를 발급한다.
                    - accessToken: HttpOnly 쿠키(Set-Cookie)로 반환 — JS에서 접근 불가, /api 경로에 자동 전송됨
                    - refreshToken: HttpOnly 쿠키(Set-Cookie)로 반환 — JS에서 접근 불가, /api/v1/auth/reissue 경로에만 전송됨"""
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "로그인 성공 (AUTH_200)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = OAuthLoginResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "code": "AUTH_200",
                                      "message": "로그인에 성공했습니다.",
                                      "data": {
                                        "onboardingCompleted": true,
                                        "nickname": "김경민"
                                      }
                                    }
                                    """)
                    )
            ),
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
            description = """
                    HttpOnly 쿠키로 전달된 refreshToken을 검증하고 accessToken과 refreshToken을 재발급한다.
                    - refreshToken: 요청 쿠키에서 자동 전송 (브라우저가 HttpOnly 쿠키를 자동 첨부)
                    - 새 accessToken: HttpOnly 쿠키(Set-Cookie)로 갱신
                    - 새 refreshToken: HttpOnly 쿠키(Set-Cookie)로 갱신"""
    )
    @Parameter(
            name = "refreshToken",
            in = ParameterIn.COOKIE,
            description = "HttpOnly 쿠키로 전달된 refreshToken (브라우저가 자동 첨부)",
            required = true
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "재발급 성공 (AUTH_200_2)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "code": "AUTH_200_2",
                                      "message": "토큰이 재발급되었습니다.",
                                      "data": null
                                    }
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "만료된 refreshToken(AUTH_002), 유효하지 않은 refreshToken(AUTH_001), "
                            + "쿠키 없음 또는 저장된 토큰과 불일치(AUTH_005)")
    })
    @interface Reissue {
    }
}