package com.growingpots.domain.university.controller;

import com.growingpots.domain.university.dto.response.OnboardingOptionsResponse;
import com.growingpots.domain.university.service.OnboardingService;
import com.growingpots.global.response.BaseResponse;
import com.growingpots.global.response.success.SuccessCode;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@OnboardingApi
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/onboarding")
public class OnboardingController {

    private final OnboardingService onboardingService;

    @OnboardingApi.GetOptions
    @GetMapping("/options")
    public ResponseEntity<BaseResponse<OnboardingOptionsResponse>> getOptions(
            @Parameter(description = "학교 ID (미전달 시 전체 학교/학과 반환)", example = "1")
            @RequestParam(required = false) Long schoolId) {
        OnboardingOptionsResponse response = onboardingService.getOptions(schoolId);
        return ResponseEntity.ok(BaseResponse.success(SuccessCode.ONBOARDING_OPTIONS_FOUND, response));
    }
}