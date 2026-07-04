package com.growingpots.domain.user.controller;

import com.growingpots.domain.user.dto.OAuthLoginRequest;
import com.growingpots.domain.user.dto.OAuthLoginResponse;
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

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/oauth/login")
    public ResponseEntity<BaseResponse<OAuthLoginResponse>> login(@Valid @RequestBody OAuthLoginRequest request) {
        OAuthLoginResponse response = authService.login(request);
        return ResponseEntity.status(SuccessCode.LOGIN_SUCCESS.getStatus())
                .body(BaseResponse.success(SuccessCode.LOGIN_SUCCESS, response));
    }
}
