package com.growingpots.domain.graduation.controller;

import com.growingpots.domain.graduation.dto.response.GraduationCourseResponse;
import com.growingpots.domain.graduation.dto.response.GraduationResponse;
import com.growingpots.domain.graduation.enums.MajorTypeFilter;
import com.growingpots.domain.graduation.service.GraduationService;
import com.growingpots.global.response.BaseResponse;
import com.growingpots.global.response.success.SuccessCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@GraduationApi
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/students/me/graduation")
public class GraduationController {

    private final GraduationService graduationService;

    @GraduationApi.GetGraduationStatus
    @GetMapping
    public ResponseEntity<BaseResponse<GraduationResponse>> getGraduation(
            @RequestParam(defaultValue = "ALL") MajorTypeFilter majorType,
            @RequestParam(defaultValue = "COMPLETED") String source, // TODO(planner-source): PLANNED 구현 시 enum으로 전환
            Authentication authentication
    ) {
        Long memberId = Long.parseLong(authentication.getName());
        GraduationResponse response = graduationService.getGraduation(memberId, majorType);
        return ResponseEntity.ok(BaseResponse.success(SuccessCode.GRADUATION_STATUS_FOUND, response));
    }

    @GraduationApi.GetDivisionCourses
    @GetMapping("/{divisionCode}/courses")
    public ResponseEntity<BaseResponse<GraduationCourseResponse>> getDivisionCourses(
            @PathVariable String divisionCode,
            @RequestParam(defaultValue = "ALL") MajorTypeFilter majorType,
            Authentication authentication
    ) {
        Long memberId = Long.parseLong(authentication.getName());
        GraduationCourseResponse response = graduationService.getCoursesByDivision(memberId, divisionCode, majorType);
        return ResponseEntity.ok(BaseResponse.success(SuccessCode.GRADUATION_COURSE_FOUND, response));
    }
}