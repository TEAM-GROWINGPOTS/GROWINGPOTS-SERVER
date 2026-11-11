package com.growingpots.domain.user.controller;

import com.growingpots.domain.user.dto.request.OAuthLoginRequest;
import com.growingpots.domain.user.dto.response.OAuthLoginResponse;
import com.growingpots.domain.user.dto.response.TokenReissueResponse;
import com.growingpots.domain.user.service.AuthService;
import com.growingpots.global.exception.BaseException;
import com.growingpots.global.response.BaseResponse;
import com.growingpots.global.response.error.ErrorCode;
import com.growingpots.global.response.success.SuccessCode;
import com.growingpots.global.security.AuthCookieProvider;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
    private final AuthCookieProvider authCookieProvider;

    @AuthApi.Login
    @PostMapping("/oauth/login")
    public ResponseEntity<BaseResponse<OAuthLoginResponse>> login(
            @Valid @RequestBody OAuthLoginRequest request,
            HttpServletResponse response) {
        AuthService.LoginResult result = authService.login(request);
        authCookieProvider.setAuthCookies(response, result.accessToken(), result.refreshToken());
        OAuthLoginResponse body = new OAuthLoginResponse(
                result.accessToken(), result.onboardingCompleted(), result.nickname());
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
        authCookieProvider.setAuthCookies(response, result.accessToken(), result.refreshToken());
        return ResponseEntity.status(SuccessCode.TOKEN_REISSUED.getStatus())
                .body(BaseResponse.success(SuccessCode.TOKEN_REISSUED, new TokenReissueResponse(result.accessToken())));
    }

    @AuthApi.Logout
    @PostMapping("/logout")
    public ResponseEntity<BaseResponse<Void>> logout(
            @CookieValue(name = "refreshToken", required = false) String refreshToken,
            HttpServletResponse response) {
        // 멱등: 쿠키가 없든 DB 값과 다르든 서버 무효화는 조용히 끝내고, 인증 쿠키는 항상 삭제한다.
        authService.logout(refreshToken);
        authCookieProvider.clearAuthCookies(response);
        return ResponseEntity.status(SuccessCode.LOGOUT_SUCCESS.getStatus())
                .body(BaseResponse.success(SuccessCode.LOGOUT_SUCCESS));
    }
}
