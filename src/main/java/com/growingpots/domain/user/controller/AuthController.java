package com.growingpots.domain.user.controller;

import com.growingpots.domain.user.dto.request.OAuthLoginRequest;
import com.growingpots.domain.user.dto.request.TokenReissueRequest;
import com.growingpots.domain.user.dto.response.OAuthLoginResponse;
import com.growingpots.domain.user.dto.response.TokenReissueResponse;
import com.growingpots.domain.user.service.AuthService;
import com.growingpots.global.response.BaseResponse;
import com.growingpots.global.response.success.SuccessCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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

    @AuthApi.Login
    @PostMapping("/oauth/login")
    public ResponseEntity<BaseResponse<OAuthLoginResponse>> login(@Valid @RequestBody OAuthLoginRequest request) {
        OAuthLoginResponse response = authService.login(request);
        return ResponseEntity.status(SuccessCode.LOGIN_SUCCESS.getStatus())
                .body(BaseResponse.success(SuccessCode.LOGIN_SUCCESS, response));
    }

    @AuthApi.Reissue
    @PostMapping("/reissue")
    public ResponseEntity<BaseResponse<TokenReissueResponse>> reissue(@Valid @RequestBody TokenReissueRequest request) {
        TokenReissueResponse response = authService.reissue(request);
        return ResponseEntity.status(SuccessCode.TOKEN_REISSUED.getStatus())
                .body(BaseResponse.success(SuccessCode.TOKEN_REISSUED, response));
    }
}
