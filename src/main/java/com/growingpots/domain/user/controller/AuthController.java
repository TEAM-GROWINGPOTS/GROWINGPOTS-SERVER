package com.growingpots.domain.user.controller;

import com.growingpots.domain.user.dto.request.OAuthLoginRequest;
import com.growingpots.domain.user.dto.response.OAuthLoginResponse;
import com.growingpots.domain.user.service.AuthService;
import com.growingpots.global.response.BaseResponse;
import com.growingpots.global.response.success.SuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "소셜 로그인 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

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
    @PostMapping("/oauth/login")
    public ResponseEntity<BaseResponse<OAuthLoginResponse>> login(@Valid @RequestBody OAuthLoginRequest request) {
        OAuthLoginResponse response = authService.login(request);
        return ResponseEntity.status(SuccessCode.LOGIN_SUCCESS.getStatus())
                .body(BaseResponse.success(SuccessCode.LOGIN_SUCCESS, response));
    }
}
