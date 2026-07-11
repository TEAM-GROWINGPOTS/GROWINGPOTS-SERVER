package com.growingpots.domain.user.controller;

import com.growingpots.domain.user.dto.request.OAuthLoginRequest;
import com.growingpots.domain.user.dto.response.OAuthLoginResponse;
import com.growingpots.domain.user.dto.response.TokenReissueResponse;
import com.growingpots.domain.user.service.AuthService;
import com.growingpots.global.exception.BaseException;
import com.growingpots.global.response.BaseResponse;
import com.growingpots.global.response.error.ErrorCode;
import com.growingpots.global.response.success.SuccessCode;
import com.growingpots.global.security.JwtTokenProvider;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@AuthApi
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtTokenProvider jwtTokenProvider;

    @Value("${cookie.secure:true}")
    private boolean cookieSecure;

    @AuthApi.Login
    @PostMapping("/oauth/login")
    public ResponseEntity<BaseResponse<OAuthLoginResponse>> login(
            @Valid @RequestBody OAuthLoginRequest request,
            HttpServletResponse response) {
        AuthService.LoginResult result = authService.login(request);
        response.addHeader(HttpHeaders.SET_COOKIE, buildRefreshCookie(result.refreshToken()).toString());
        OAuthLoginResponse body = new OAuthLoginResponse(result.accessToken(), result.onboardingCompleted(), result.nickname());
        return ResponseEntity.status(SuccessCode.LOGIN_SUCCESS.getStatus())
                .body(BaseResponse.success(SuccessCode.LOGIN_SUCCESS, body));
    }

    @AuthApi.Reissue
    @PostMapping("/reissue")
    public ResponseEntity<BaseResponse<TokenReissueResponse>> reissue(
            @CookieValue(name = "refreshToken", required = false) String refreshToken,
            HttpServletResponse response) {
        if (refreshToken == null) {
            throw new BaseException(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
        }
        AuthService.ReissueResult result = authService.reissue(refreshToken);
        response.addHeader(HttpHeaders.SET_COOKIE, buildRefreshCookie(result.refreshToken()).toString());
        return ResponseEntity.status(SuccessCode.TOKEN_REISSUED.getStatus())
                .body(BaseResponse.success(SuccessCode.TOKEN_REISSUED, new TokenReissueResponse(result.accessToken())));
    }

    private ResponseCookie buildRefreshCookie(String token) {
        return ResponseCookie.from("refreshToken", token)
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/api/v1/auth/reissue")
                .maxAge(jwtTokenProvider.getRefreshExpirationSeconds())
                .sameSite("Lax")
                .build();
    }
}