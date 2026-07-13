package com.growingpots.domain.user.controller;

import com.growingpots.domain.user.dto.response.StudentCourseListResponse;
import com.growingpots.domain.user.dto.response.StudentProfileCreateResponse;
import com.growingpots.domain.user.dto.response.StudentProfileResponse;
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
                    description = "잘못된 요청 (CMN_002: 필수값 누락 / UNIV_003: 학과가 해당 학교 소속이 아님)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "code": "UNIV_003",
                                      "message": "해당 학교에 속하지 않는 학과입니다.",
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
                    responseCode = "409",
                    description = "이미 온보딩 완료된 사용자",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "code": "USER_002",
                                      "message": "이미 온보딩 완료된 사용자입니다.",
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

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @Operation(
            summary = "내 학적 정보 조회",
            description = "JWT로 인증된 사용자의 학적 정보를 조회합니다. PDF 미업로드 상태면 studentNo, gradeLevel, semester, enrollmentStatus는 null입니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "학적 정보 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = StudentProfileResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "code": "USER_200_2",
                                      "message": "학적 정보 조회에 성공했습니다.",
                                      "data": {
                                        "studentProfileId": 5001,
                                        "name": "김경민",
                                        "schoolName": "경희대학교 국제캠퍼스",
                                        "departmentName": "연극영화학과",
                                        "studentNo": null,
                                        "admissionYear": 2023,
                                        "gradeLevel": null,
                                        "semester": null,
                                        "enrollmentStatus": null,
                                        "majors": [
                                          {
                                            "studentMajorId": 9001,
                                            "majorType": "MAIN",
                                            "departmentName": "연극영화학과",
                                            "trackName": null
                                          }
                                        ]
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
                    description = "온보딩 미완료 (학적 프로필 없음)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "code": "USER_003",
                                      "message": "온보딩이 완료되지 않은 사용자입니다.",
                                      "data": null
                                    }
                                    """)
                    )
            )
    })
    @interface GetMyProfile {}

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @Operation(
            summary = "이수 과목 목록 조회",
            description = "PDF 분석 결과로 저장된 이수 과목 목록을 조회합니다. "
                    + "departmentName은 COURSE 마스터와 매칭되었거나 학과가 직접 지정된 경우에만, appliedDivisionName은 "
                    + "이수구분이 지정된 경우에만 값이 채워집니다. 교양 과목인데 매칭된 학과가 없으면 학교에 지정된 대체 "
                    + "학과(예: 후마니타스칼리지)가 있을 때 그 이름으로, 없으면 \"교양\"이라는 표시용 문자열로 채워집니다. "
                    + "departmentId/appliedDivisionId는 각각 departmentName/appliedDivisionName의 근거가 된 PK로, 이 과목을 "
                    + "수정 없이 그대로 PUT /students/me/courses에 다시 보낼 때 departmentId/appliedDivisionId에 그대로 넣으면 "
                    + "된다. name이 null이거나 \"교양\" 표시용 문자열이면(학교에 대체 학과가 없는 경우) 대응하는 id도 null인데, "
                    + "이 경우엔 애초에 연결된 실제 학과가 없다는 뜻이라 null을 그대로 보내도 기존 값이 지워지지 않는다(원래 없던 "
                    + "걸 다시 없다고 보내는 것). 반대로 name이 실제 학과 기반인데 id를 비워서 보내면 서버가 null로 덮어써 "
                    + "기존 값이 지워지므로 주의."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "이수 과목 목록 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = StudentCourseListResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "code": "USER_200_3",
                                      "message": "이수 과목을 조회했습니다.",
                                      "data": {
                                        "courses": [
                                          {
                                            "studentCourseId": 7001,
                                            "courseCode": "THE2001",
                                            "name": "연극문헌과연기",
                                            "departmentName": "연극영화학과",
                                            "departmentId": 12,
                                            "credit": 3,
                                            "appliedDivisionName": "전공필수",
                                            "appliedDivisionId": 4,
                                            "takenYear": 2023,
                                            "takenSemester": "1학기"
                                          }
                                        ]
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
                    description = "온보딩 미완료 (학적 프로필 없음)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "code": "USER_003",
                                      "message": "온보딩이 완료되지 않은 사용자입니다.",
                                      "data": null
                                    }
                                    """)
                    )
            )
    })
    @interface GetMyCourses {}

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @Operation(
            summary = "이수 과목 검수/저장",
            description = "분석 확인 화면의 편집모드에서 수정·추가·삭제한 이수 과목 전체를 저장합니다. "
                    + "studentCourseId가 있으면 수정, 없으면 직접 추가한 과목으로 신규 생성됩니다. "
                    + "요청 목록에 없는 기존 과목은 삭제됩니다(편집모드 상태를 그대로 반영하는 전체 교체 방식)."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "이수 과목 저장 성공",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "code": "USER_200_4",
                                      "message": "이수 과목이 저장되었습니다.",
                                      "data": null
                                    }
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 입력값 (다른 학생의 studentCourseId, 존재하지 않는 courseId/departmentId/appliedDivisionId 포함)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "code": "CMN_002",
                                      "message": "잘못된 입력값입니다.",
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
                    responseCode = "404",
                    description = "온보딩 미완료 (학적 프로필 없음)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "code": "USER_003",
                                      "message": "온보딩이 완료되지 않은 사용자입니다.",
                                      "data": null
                                    }
                                    """)
                    )
            )
    })
    @interface UpdateMyCourses {}
}