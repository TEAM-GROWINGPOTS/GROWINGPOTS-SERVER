package com.growingpots.domain.graduation.controller;

import com.growingpots.domain.graduation.dto.response.GraduationCourseResponse;
import com.growingpots.domain.graduation.dto.response.GraduationResponse;
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
@Tag(name = "Graduation", description = "졸업 현황 조회 API")
public @interface GraduationApi {

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @Operation(
            summary = "졸업 현황 조회",
            description = """
                    PDF 스냅샷 기반 졸업 현황을 반환한다.

                    **majorType별 반환 조건**
                    - ALL: 본전공·복수전공·교양·기타 4섹션 분리 (sections 사용, conditions=null)
                    - PRIMARY: 본전공 MAJOR_* + 영어/SW
                    - MULTI: 복수전공 MAJOR_* + 영어/SW
                    - GE: REQUIRED_GE·DISTRIBUTED_GE·FREE_GE + 영어/SW
                    - OTHERS: GENERAL_ELECTIVE 단일 조건, 영어/SW 미포함

                    영어/SW 강의는 appliedDivision.category 기준으로 전공·교양 탭에만 배치된다.

                    **DISTRIBUTED_GE satisfied**
                    24학번 이상: 학점 충족 AND 5개 영역 중 3개 이상 이수를 모두 충족해야 true.
                    19~23학번: 학점 기준만 적용.

                    **graduationRequired**
                    학과 독립 졸업요건이 있을 때만 채워짐 (PRIMARY/MULTI 및 ALL의 sections.primary/multi). 그 외 null.
                    totalCredit은 연결 과목 중 이수한 학점 합계.
                    unmetDescriptions는 학점 기준 하위조건 문구만 포함 (과목수 기준 조건은 과목 카드로만 표시).

                    **source=PLANNED**
                    플래너의 미이수/미수강 계획 과목을 스냅샷에 합산한 예상 졸업현황.
                    플래너가 없거나 신규 계획 과목이 없으면 COMPLETED와 동일.
                    이수구분 미지정 계획 과목은 GENERAL_ELECTIVE로 처리된다."""
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "졸업 현황 조회 성공 (REQ_200_1)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = GraduationResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "code": "REQ_200_1",
                                      "message": "졸업 현황 조회에 성공했습니다.",
                                      "data": {
                                        "summary": {
                                          "totalCredits": { "current": 87, "required": 130 },
                                          "gpa": { "current": 3.85, "min": 2.0 },
                                          "enrollmentStatus": "재학"
                                        },
                                        "graduatable": false,
                                        "conditions": null,
                                        "graduationRequired": null,
                                        "sections": {
                                          "primary": {
                                            "majorName": "컴퓨터공학과",
                                            "conditions": [
                                              { "code": "MAJOR_BASIC",    "name": "전공 기초", "current": 18, "required": 18, "unit": "CREDITS", "satisfied": true,  "chartTarget": true  },
                                              { "code": "MAJOR_REQUIRED", "name": "전공 필수", "current": 12, "required": 21, "unit": "CREDITS", "satisfied": false, "chartTarget": true  },
                                              { "code": "MAJOR_ELECTIVE", "name": "전공 선택", "current": 15, "required": 24, "unit": "CREDITS", "satisfied": false, "chartTarget": true  },
                                              { "code": "ENGLISH_COURSE",  "name": "영어 강의",    "current": 2, "required": 3, "unit": "COURSES", "satisfied": false, "chartTarget": false },
                                              { "code": "SW_CERT_COURSE",  "name": "SW 인증 강의", "current": 1, "required": 1, "unit": "COURSES", "satisfied": true,  "chartTarget": false }
                                            ],
                                            "graduationRequired": null
                                          },
                                          "multi": null,
                                          "ge": {
                                            "majorName": null,
                                            "conditions": [
                                              { "code": "REQUIRED_GE",    "name": "필수 교과",      "current": 6,  "required": 6,  "unit": "CREDITS", "satisfied": true,  "chartTarget": true  },
                                              { "code": "DISTRIBUTED_GE", "name": "배분 이수 교과", "current": 9,  "required": 12, "unit": "CREDITS", "satisfied": false, "chartTarget": true  },
                                              { "code": "FREE_GE",        "name": "자유 이수 교과", "current": 6,  "required": 6,  "unit": "CREDITS", "satisfied": true,  "chartTarget": true  },
                                              { "code": "ENGLISH_COURSE",  "name": "영어 강의",    "current": 2, "required": 3, "unit": "COURSES", "satisfied": false, "chartTarget": false },
                                              { "code": "SW_CERT_COURSE",  "name": "SW 인증 강의", "current": 1, "required": 1, "unit": "COURSES", "satisfied": true,  "chartTarget": false }
                                            ],
                                            "graduationRequired": null
                                          },
                                          "others": {
                                            "majorName": null,
                                            "conditions": [
                                              { "code": "GENERAL_ELECTIVE", "name": "일반 선택", "current": 21, "required": null, "unit": "CREDITS", "satisfied": true, "chartTarget": false }
                                            ],
                                            "graduationRequired": null
                                          }
                                        },
                                        "certs": [
                                          { "certType": "ENGLISH", "result": "PASS" },
                                          { "certType": "SW",      "result": "FAIL" },
                                          { "certType": "TOPIK",   "result": "NONE" },
                                          { "certType": "THESIS",  "result": "NONE" }
                                        ]
                                      }
                                    }
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패 (CMN_005)",
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
                    description = "학적 정보 없음(USER_003) / 졸업 분석 데이터 없음(REQ_001)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(name = "USER_003", summary = "온보딩 미완료", value = """
                                            {
                                              "success": false,
                                              "code": "USER_003",
                                              "message": "온보딩이 완료되지 않은 사용자입니다.",
                                              "data": null
                                            }
                                            """),
                                    @ExampleObject(name = "REQ_001", summary = "졸업 분석 데이터 없음", value = """
                                            {
                                              "success": false,
                                              "code": "REQ_001",
                                              "message": "졸업요건 데이터가 존재하지 않습니다.",
                                              "data": null
                                            }
                                            """)
                            }
                    )
            )
    })
    @interface GetGraduationStatus {
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @Operation(
            summary = "이수구분별 과목 조회",
            description = """
                    divisionCode에 해당하는 이수/미이수 과목 목록을 반환한다.
                    hasRequiredList=true이면 미이수 필수과목이 courses에 포함된다.

                    **majorType별 동작**
                    - ALL: 보유 전공 전부
                    - PRIMARY: 본전공 / MULTI: 복수전공 / GE·OTHERS: 본전공 스냅샷 기준
                    - ENGLISH_COURSE·SW_CERT_COURSE + ALL: 탭·학과 구분 없이 전체 합산 (majors 1개, majorType=null)
                    - ENGLISH_COURSE·SW_CERT_COURSE + PRIMARY/MULTI/GE: 해당 탭 이수구분에 속하는 과목만 반환
                    - ENGLISH_COURSE·SW_CERT_COURSE + OTHERS: 기타 섹션에 조건이 없어 majors=[] 빈 응답

                    **DISTRIBUTED_GE**
                    24학번 이상: areaRequirement 블록이 채워짐 (5개 영역 완료 현황).
                    satisfied는 학점 충족 AND 3개 이상 영역 이수를 모두 만족해야 true.
                    19~23학번: areaRequirement=null, satisfied는 학점 기준만 적용.
                    각 과목 카드의 area 필드에 배분이수 영역 정보(코드·이름)가 담김 (영역 미지정 과목은 null).

                    **GRADUATION_REQUIRED**
                    학과 독립 졸업요건에 연결된 과목들을 이수/미이수 하나의 리스트로 반환.
                    current/required는 만족한 조건 수/전체 조건 수.
                    unmetDescriptions에 학점 기준 하위조건의 미충족 문구만 포함 (과목수 기준 조건은 과목 카드로만 표시).
                    해당 학과가 아니면 결과가 비어 있다 (hasRequiredList=false)."""
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공 (REQ_200_2)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = GraduationCourseResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "code": "REQ_200_2",
                                      "message": "이수구분별 과목을 조회했습니다.",
                                      "data": {
                                        "divisionCode": "MAJOR_REQUIRED",
                                        "divisionName": "전공 필수",
                                        "majors": [
                                          {
                                            "majorType": "MAIN",
                                            "departmentName": "컴퓨터공학과",
                                            "current": 12,
                                            "required": 21,
                                            "satisfied": false,
                                            "hasRequiredList": true,
                                            "unmetDescriptions": [],
                                            "courses": [
                                              {
                                                "studentCourseId": 101,
                                                "name": "자료구조",
                                                "departmentName": "컴퓨터공학과",
                                                "credit": 3,
                                                "semester": "1학기",
                                                "taken": true,
                                                "isEnglish": false,
                                                "isSw": false
                                              },
                                              {
                                                "studentCourseId": null,
                                                "name": "운영체제",
                                                "departmentName": "컴퓨터공학과",
                                                "credit": 3,
                                                "semester": "1학기",
                                                "taken": false,
                                                "isEnglish": false,
                                                "isSw": false
                                              }
                                            ]
                                          }
                                        ]
                                      }
                                    }
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 divisionCode (CMN_002)",
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
                    description = "인증 실패 (CMN_005)",
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
                    description = "학적 정보 없음(USER_003) / 졸업 분석 데이터 없음(REQ_001) / 복수전공 없음(REQ_002)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(name = "USER_003", summary = "온보딩 미완료", value = """
                                            {
                                              "success": false,
                                              "code": "USER_003",
                                              "message": "온보딩이 완료되지 않은 사용자입니다.",
                                              "data": null
                                            }
                                            """),
                                    @ExampleObject(name = "REQ_001", summary = "졸업 분석 데이터 없음", value = """
                                            {
                                              "success": false,
                                              "code": "REQ_001",
                                              "message": "졸업요건 데이터가 존재하지 않습니다.",
                                              "data": null
                                            }
                                            """),
                                    @ExampleObject(name = "REQ_002", summary = "복수전공 없음", value = """
                                            {
                                              "success": false,
                                              "code": "REQ_002",
                                              "message": "복수전공이 등록되지 않은 사용자입니다.",
                                              "data": null
                                            }
                                            """)
                            }
                    )
            )
    })
    @interface GetDivisionCourses {
    }
}