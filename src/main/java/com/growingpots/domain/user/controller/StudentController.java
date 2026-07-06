package com.growingpots.domain.user.controller;

import com.growingpots.domain.user.dto.request.StudentProfileCreateRequest;
import com.growingpots.domain.user.dto.response.StudentProfileCreateResponse;
import com.growingpots.domain.user.dto.response.StudentProfileResponse;
import com.growingpots.domain.user.service.StudentProfileService;
import com.growingpots.global.response.BaseResponse;
import com.growingpots.global.response.success.SuccessCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@StudentApi
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/students")
public class StudentController {

    private final StudentProfileService studentProfileService;

    @StudentApi.CreateStudentProfile
    @PostMapping
    public ResponseEntity<BaseResponse<StudentProfileCreateResponse>> createStudentProfile(
            @Valid @RequestBody StudentProfileCreateRequest request,
            Authentication authentication) {
        Long memberId = Long.parseLong(authentication.getName());
        StudentProfileCreateResponse response = studentProfileService.create(memberId, request);
        return ResponseEntity.status(SuccessCode.STUDENT_PROFILE_CREATED.getStatus())
                .body(BaseResponse.success(SuccessCode.STUDENT_PROFILE_CREATED, response));
    }

    @StudentApi.GetMyProfile
    @GetMapping("/me")
    public ResponseEntity<BaseResponse<StudentProfileResponse>> getMyProfile(Authentication authentication) {
        Long memberId = Long.parseLong(authentication.getName());
        StudentProfileResponse response = studentProfileService.getMyProfile(memberId);
        return ResponseEntity.ok(BaseResponse.success(SuccessCode.STUDENT_PROFILE_FOUND, response));
    }
}