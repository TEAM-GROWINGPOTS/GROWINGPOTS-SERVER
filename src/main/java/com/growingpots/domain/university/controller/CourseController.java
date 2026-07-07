package com.growingpots.domain.university.controller;

import com.growingpots.domain.university.dto.request.CourseSearchRequest;
import com.growingpots.domain.university.dto.response.CourseSearchResponse;
import com.growingpots.domain.university.service.CourseService;
import com.growingpots.global.response.BaseResponse;
import com.growingpots.global.response.success.SuccessCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@CourseApi
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/courses")
public class CourseController {

    private final CourseService courseService;

    @CourseApi.SearchCourses
    @GetMapping
    public ResponseEntity<BaseResponse<CourseSearchResponse>> searchCourses(
            @ModelAttribute CourseSearchRequest request, Authentication authentication) {
        Long memberId = Long.parseLong(authentication.getName());
        CourseSearchResponse response = courseService.searchCourses(memberId, request);
        return ResponseEntity.ok(BaseResponse.success(SuccessCode.COURSE_SEARCH_FOUND, response));
    }
}
