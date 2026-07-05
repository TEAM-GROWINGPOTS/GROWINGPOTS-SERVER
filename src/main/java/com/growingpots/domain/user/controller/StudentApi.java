package com.growingpots.domain.user.controller;

import com.growingpots.domain.user.dto.response.StudentProfileCreateResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Tag(name = "Student", description = "학생 프로필 관련 API")
public @interface StudentApi {

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @Operation(
            summary = "학생 프로필 생성",
            description = "학교, 학과, 입학연도를 기반으로 학생 프로필을 생성합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "학생 프로필 생성 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = StudentProfileCreateResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "code": "USER_201",
                                      "message": "학적 정보가 저장되었습니다.",
                                      "data": {
                                        "studentProfileId": 1,
                                        "mainMajor": {
                                          "studentMajorId": 1,
                                          "departmentName": "컴퓨터공학과"
                                        }
                                      }
                                    }
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청 (필수값 누락)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "code": "CMN_002",
                                      "message": "잘못된 요청입니다.",
                                      "data": null
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
    @interface CreateStudentProfile {}
}