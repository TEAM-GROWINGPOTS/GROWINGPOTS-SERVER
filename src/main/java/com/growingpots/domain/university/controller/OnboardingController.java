package com.growingpots.domain.university.controller;

import com.growingpots.domain.university.dto.response.OnboardingOptionsResponse;
import com.growingpots.domain.university.service.OnboardingService;
import com.growingpots.global.response.BaseResponse;
import com.growingpots.global.response.success.SuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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

    @Operation(
            summary = "온보딩 옵션 조회",
            description = """
                    온보딩 화면에 필요한 학교 목록, 학과 목록, 입학연도 목록을 반환합니다.

                    - `schoolId` 미전달 시 전체 학교와 전체 학과를 반환합니다.
                    - `schoolId` 전달 시 해당 학교와 해당 학교의 학과만 반환합니다.
                    - 단과대학 필터링은 응답의 `college` 필드를 기준으로 클라이언트에서 처리합니다.
                    - `admissionYears`는 현재 연도 기준 최근 8개년을 내림차순으로 반환합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "온보딩 옵션 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = OnboardingOptionsResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "code": "UNIV_200_2",
                                      "message": "온보딩 옵션 조회에 성공했습니다.",
                                      "data": {
                                        "schools": [
                                          { "schoolId": 1, "name": "경희대학교 국제캠퍼스" }
                                        ],
                                        "departments": [
                                          { "departmentId": 1, "schoolId": 1, "college": "공과대학", "name": "컴퓨터공학과" },
                                          { "departmentId": 2, "schoolId": 1, "college": "공과대학", "name": "전자공학과" }
                                        ],
                                        "admissionYears": [2026, 2025, 2024, 2023, 2022, 2021, 2020, 2019]
                                      }
                                    }
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "code": "CMN_005",
                                      "message": "인증이 필요합니다.",
                                      "data": null
                                    }
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "존재하지 않는 학교 ID",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "code": "UNIV_001",
                                      "message": "존재하지 않는 학교입니다.",
                                      "data": null
                                    }
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "서버 오류",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "code": "CMN_001",
                                      "message": "서버 오류가 발생했습니다.",
                                      "data": null
                                    }
                                    """)
                    )
            )
    })
    @GetMapping("/options")
    public ResponseEntity<BaseResponse<OnboardingOptionsResponse>> getOptions(
            @Parameter(description = "학교 ID (미전달 시 전체 학교/학과 반환)", example = "1")
            @RequestParam(required = false) Long schoolId) {
        OnboardingOptionsResponse response = onboardingService.getOptions(schoolId);
        return ResponseEntity.ok(BaseResponse.success(SuccessCode.ONBOARDING_OPTIONS_FOUND, response));
    }
}
