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

                    **전공 조회 방식**
                    본전공/복수전공을 PRIMARY·MULTI로 나누던 방식은 폐지됐다(복수전공을 여러 개 가진
                    학생도 있어 이분법으로 표현이 안 됨). 대신:
                    - studentMajorId 파라미터(GET /students/me 응답의 majors[].studentMajorId)가 있으면
                      그 전공 하나만 단건 응답(conditions/graduationRequired 채움, sections=null)을
                      반환한다. 본전공/복수전공 구분 없이 조회 가능.
                    - studentMajorId가 없으면 majorType으로 조회: ALL(보유 전공 전부 + 교양 + 기타,
                      sections 사용, conditions=null) / GE(REQUIRED_GE·DISTRIBUTED_GE·FREE_GE + 영어·SW)
                      / OTHERS(GENERAL_ELECTIVE 단일 조건, 영어·SW 미포함)

                    영어/SW 강의는 appliedDivision.category 기준으로 전공·교양 탭에만 배치된다.

                    **DISTRIBUTED_GE satisfied**
                    24학번 이상: 학점 충족 AND 5개 영역 중 3개 이상 이수를 모두 충족해야 true.
                    19~23학번: 학점 기준만 적용.

                    **graduationRequired**
                    studentMajorId로 조회 시 top-level data.graduationRequired에 채워짐. majorType=ALL
                    조회 시엔 sections.majors 각 항목(전공)마다 채워짐. 둘 다 그 학과에 이수구분과 무관한
                    독립 졸업요건(예: 스포츠의학과 졸업필수)이 실제로 있을 때만 non-null이고, 없으면
                    null(대부분의 학과) - FE는 null 여부로 그 전공에 "졸업 필수" 카드를 보여줄지 판단하면
                    됨. GE/OTHERS 탭·ALL 탭(top-level data.graduationRequired)은 항상 null.
                    totalCredit은 연결 과목 중 이수한 학점 합계.
                    unmetDescriptions는 학점 기준 하위조건 문구만 포함 (과목수 기준 조건은 과목 카드로만 표시).
                    items는 전문실기·맨손체조 같은 하위 요건별 current/required/unit/satisfied를 구조화해서 담는다 (플래너·노드뷰 화면용).

                    **source=PLANNED**
                    플래너의 미이수/미수강 계획 과목을 스냅샷에 합산한 예상 졸업현황.
                    graduationRequired도 동일하게 계획 과목을 반영한다 (예: 전문실기를 플래너에 담으면 items의 current가 올라감).
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
                            examples = {
                                    @ExampleObject(
                                            name = "ALL_복수전공_여러_개",
                                            summary = "majorType=ALL, 본전공 1개(스포츠의학과, 졸업필수 있음) + 복수전공 2개(졸업필수 없음). "
                                                    + "graduationRequired는 요건 있는 전공에만 채워지고 없는 전공은 null",
                                            value = """
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
                                          "majors": [
                                            {
                                              "majorName": "스포츠의학과",
                                              "majorType": "MAIN",
                                              "conditions": [
                                                { "code": "MAJOR_BASIC",    "name": "전공 기초", "current": 6,  "required": 7,  "unit": "CREDITS", "satisfied": false, "chartTarget": true  },
                                                { "code": "MAJOR_REQUIRED", "name": "전공 필수", "current": 9,  "required": 9,  "unit": "CREDITS", "satisfied": true,  "chartTarget": true  },
                                                { "code": "MAJOR_ELECTIVE", "name": "전공 선택", "current": 15, "required": 51, "unit": "CREDITS", "satisfied": false, "chartTarget": true  },
                                                { "code": "ENGLISH_COURSE",  "name": "영어 강의",    "current": 7, "required": 3, "unit": "COURSES", "satisfied": true,  "chartTarget": false },
                                                { "code": "SW_CERT_COURSE",  "name": "SW 인증 강의", "current": 6, "required": 6, "unit": "CREDITS", "satisfied": true,  "chartTarget": false }
                                              ],
                                              "graduationRequired": {
                                                "hasGraduationRequired": true,
                                                "satisfied": false,
                                                "totalCredit": 2,
                                                "unmetDescriptions": [],
                                                "items": [
                                                  { "name": "전문실기", "current": 1, "required": 2, "unit": "COURSES", "satisfied": false },
                                                  { "name": "맨손체조", "current": 0, "required": 1, "unit": "COURSES", "satisfied": false }
                                                ]
                                              }
                                            },
                                            {
                                              "majorName": "연극영화학과",
                                              "majorType": "DOUBLE",
                                              "conditions": [
                                                { "code": "MAJOR_BASIC",    "name": "전공 기초", "current": 3,  "required": 9,  "unit": "CREDITS", "satisfied": false, "chartTarget": true  },
                                                { "code": "MAJOR_REQUIRED", "name": "전공 필수", "current": 6,  "required": 15, "unit": "CREDITS", "satisfied": false, "chartTarget": true  },
                                                { "code": "MAJOR_ELECTIVE", "name": "전공 선택", "current": 6,  "required": 12, "unit": "CREDITS", "satisfied": false, "chartTarget": true  },
                                                { "code": "ENGLISH_COURSE",  "name": "영어 강의",    "current": 0, "required": 3, "unit": "COURSES", "satisfied": false, "chartTarget": false },
                                                { "code": "SW_CERT_COURSE",  "name": "SW 인증 강의", "current": 0, "required": 0, "unit": "CREDITS", "satisfied": true,  "chartTarget": false }
                                              ],
                                              "graduationRequired": null
                                            },
                                            {
                                              "majorName": "화학공학과",
                                              "majorType": "DOUBLE",
                                              "conditions": [
                                                { "code": "MAJOR_BASIC",    "name": "전공 기초", "current": 0,  "required": 9,  "unit": "CREDITS", "satisfied": false, "chartTarget": true  },
                                                { "code": "MAJOR_REQUIRED", "name": "전공 필수", "current": 0,  "required": 15, "unit": "CREDITS", "satisfied": false, "chartTarget": true  },
                                                { "code": "MAJOR_ELECTIVE", "name": "전공 선택", "current": 0,  "required": 12, "unit": "CREDITS", "satisfied": false, "chartTarget": true  },
                                                { "code": "ENGLISH_COURSE",  "name": "영어 강의",    "current": 0, "required": 3, "unit": "COURSES", "satisfied": false, "chartTarget": false },
                                                { "code": "SW_CERT_COURSE",  "name": "SW 인증 강의", "current": 0, "required": 0, "unit": "CREDITS", "satisfied": true,  "chartTarget": false }
                                              ],
                                              "graduationRequired": null
                                            }
                                          ],
                                          "ge": {
                                            "majorName": null,
                                            "majorType": null,
                                            "conditions": [
                                              { "code": "REQUIRED_GE",    "name": "필수 교과",      "current": 12, "required": 17, "unit": "CREDITS", "satisfied": false, "chartTarget": true  },
                                              { "code": "DISTRIBUTED_GE", "name": "배분 이수 교과", "current": 3,  "required": 9,  "unit": "CREDITS", "satisfied": false, "chartTarget": true  },
                                              { "code": "FREE_GE",        "name": "자유 이수 교과", "current": 5,  "required": 3,  "unit": "CREDITS", "satisfied": true,  "chartTarget": true  },
                                              { "code": "ENGLISH_COURSE",  "name": "영어 강의",    "current": 7, "required": 3, "unit": "COURSES", "satisfied": true,  "chartTarget": false },
                                              { "code": "SW_CERT_COURSE",  "name": "SW 인증 강의", "current": 6, "required": 6, "unit": "CREDITS", "satisfied": true,  "chartTarget": false }
                                            ],
                                            "graduationRequired": null
                                          },
                                          "others": {
                                            "majorName": null,
                                            "majorType": null,
                                            "conditions": [
                                              { "code": "GENERAL_ELECTIVE", "name": "기타", "current": 21, "required": null, "unit": "CREDITS", "satisfied": false, "chartTarget": false }
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
                                    """),
                                    @ExampleObject(
                                            name = "studentMajorId_졸업필수_있는_학과",
                                            summary = "studentMajorId로 특정 전공 하나만 조회, 졸업필수 요건이 있는 경우(hasGraduationRequired=true, items에 하위 요건별 진행 현황)",
                                            value = """
                                    {
                                      "success": true,
                                      "code": "REQ_200_1",
                                      "message": "졸업 현황 조회에 성공했습니다.",
                                      "data": {
                                        "summary": {
                                          "totalCredits": { "current": 45, "required": 130 },
                                          "gpa": { "current": 3.42, "min": 2.0 },
                                          "enrollmentStatus": "재학"
                                        },
                                        "graduatable": false,
                                        "conditions": [
                                          { "code": "MAJOR_BASIC",    "name": "전공 기초", "current": 9,  "required": 12, "unit": "CREDITS", "satisfied": false, "chartTarget": true  },
                                          { "code": "MAJOR_REQUIRED", "name": "전공 필수", "current": 15, "required": 30, "unit": "CREDITS", "satisfied": false, "chartTarget": true  },
                                          { "code": "MAJOR_ELECTIVE", "name": "전공 선택", "current": 6,  "required": 21, "unit": "CREDITS", "satisfied": false, "chartTarget": true  },
                                          { "code": "ENGLISH_COURSE",  "name": "영어 강의",    "current": 1, "required": 3, "unit": "COURSES", "satisfied": false, "chartTarget": false },
                                          { "code": "SW_CERT_COURSE",  "name": "SW 인증 강의", "current": 3, "required": 6, "unit": "CREDITS", "satisfied": false, "chartTarget": false }
                                        ],
                                        "graduationRequired": {
                                          "hasGraduationRequired": true,
                                          "satisfied": false,
                                          "totalCredit": 2,
                                          "unmetDescriptions": [],
                                          "items": [
                                            { "name": "전문실기", "current": 1, "required": 2, "unit": "COURSES", "satisfied": false },
                                            { "name": "맨손체조", "current": 0, "required": 1, "unit": "COURSES", "satisfied": false }
                                          ]
                                        },
                                        "sections": null,
                                        "certs": [
                                          { "certType": "ENGLISH", "result": "NONE" },
                                          { "certType": "SW",      "result": "NONE" },
                                          { "certType": "TOPIK",   "result": "NONE" },
                                          { "certType": "THESIS",  "result": "NONE" }
                                        ]
                                      }
                                    }
                                    """)
                            }
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
                    description = "학적 정보 없음(USER_003) / 졸업 분석 데이터 없음(REQ_001) / 요청한 학과가 학생의 전공에 없음(REQ_002)",
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
                                    @ExampleObject(name = "REQ_002", summary = "studentMajorId가 학생의 전공에 없음", value = """
                                            {
                                              "success": false,
                                              "code": "REQ_002",
                                              "message": "요청한 학과가 학생의 전공에 등록되어 있지 않습니다.",
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

                    **전공 조회 방식**
                    PRIMARY·MULTI 이분법은 폐지됐다. studentMajorId 파라미터가 있으면 그 전공 하나만
                    majors 배열에 담아 반환(본전공/복수전공 구분 없이 조회 가능). 없으면 majorType으로
                    조회: ALL(보유 전공 전부) / GE·OTHERS(본전공 스냅샷 기준)

                    **majorType별 동작(studentMajorId 없을 때)**
                    - ENGLISH_COURSE·SW_CERT_COURSE + ALL: 탭·학과 구분 없이 전체 합산 (majors 1개, majorType=null)
                    - ENGLISH_COURSE·SW_CERT_COURSE + GE: 해당 탭 이수구분에 속하는 과목만 반환
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
                                            "departmentName": "화학공학과",
                                            "current": 9,
                                            "required": 15,
                                            "satisfied": false,
                                            "hasRequiredList": true,
                                            "unmetDescriptions": [],
                                            "areaRequirement": null,
                                            "courses": [
                                              {
                                                "studentCourseId": 101,
                                                "name": "화공열역학1",
                                                "departmentName": "화학공학과",
                                                "credit": 3,
                                                "semester": "1학기",
                                                "taken": true,
                                                "isEnglish": false,
                                                "isSw": false,
                                                "area": null
                                              },
                                              {
                                                "studentCourseId": null,
                                                "name": "반응공학",
                                                "departmentName": "화학공학과",
                                                "credit": 3,
                                                "semester": "1학기",
                                                "taken": false,
                                                "isEnglish": false,
                                                "isSw": false,
                                                "area": null
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
                    description = "학적 정보 없음(USER_003) / 졸업 분석 데이터 없음(REQ_001) / 요청한 학과가 학생의 전공에 없음(REQ_002)",
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
                                    @ExampleObject(name = "REQ_002", summary = "studentMajorId가 학생의 전공에 없음", value = """
                                            {
                                              "success": false,
                                              "code": "REQ_002",
                                              "message": "요청한 학과가 학생의 전공에 등록되어 있지 않습니다.",
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