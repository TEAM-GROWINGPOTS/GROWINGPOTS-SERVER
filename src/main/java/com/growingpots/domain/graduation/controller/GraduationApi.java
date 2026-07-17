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
                    - studentMajorId 있음: 그 전공 하나의 단건 응답. conditions·graduationRequired 채워짐. sections=null.
                    - majorType=ALL(기본): 보유 전공 전부 + 교양 + 기타. sections 채워짐. conditions=null.
                    - majorType=GE: REQUIRED_GE·DISTRIBUTED_GE·FREE_GE + SW. conditions 채워짐. 영어는 미포함.
                    - majorType=OTHERS: GENERAL_ELECTIVE 단일 조건. 영어·SW 미포함.
                    - 영어 강의는 appliedDivision.category 기준으로 전공 탭에만 배치된다. SW는 전공·교양 탭 모두.

                    **공통 응답 필드**
                    - summary.totalCredits: current(현재 이수 학점) / required(졸업 필요 학점).
                    - summary.gpa: current(현재 누적 평점) / min(졸업 최소 요구 평점. 요건 없으면 null).
                    - summary.enrollmentStatus: 재학 상태(예: 재학 중, 휴학 중). PDF 미업로드 시 null.
                    - graduatable: 총 이수학점·학점 요건·비학점 인증·평점 요건을 모두 충족한 경우 true.
                    - curriculumSatisfied: 총 이수학점·카테고리별 학점(전공기초/전공필수/전공선택/교양 각각)·전공필수·전공기초·필수교과 개별 과목 이수·배분이수 영역·졸업필수·영어·SW 수강 기준을 충족한 경우 true. 평점·비학점 인증 요건은 제외.
                    - certs[].certType: THESIS | GRADUATION_CERT | ENGLISH | SW | TOPIK.
                    - certs[].result: PASS(통과) | FAIL(미통과, 졸업 불가 처리) | EXEMPT(면제) | NONE(해당없음).

                    **studentMajorId 조회 시 응답 필드**
                    - conditions[].code: 이수구분 코드(MAJOR_BASIC / MAJOR_REQUIRED / MAJOR_ELECTIVE / ENGLISH_COURSE / SW_CERT_COURSE).
                    - conditions[].current / required: 현재 이수량 / 요구량. unit에 따라 학점 또는 과목 수.
                    - conditions[].unit: CREDITS(학점 합계) | COURSES(과목 수).
                    - conditions[].satisfied: 요건 충족 여부.
                    - conditions[].chartTarget: 원형 차트 포함 여부. GENERAL_ELECTIVE는 항상 false.
                    - graduationRequired: 학과 독립 졸업요건. 요건이 없는 학과(대부분)는 null. null 여부로 "졸업 필수" 카드 노출 판단.

                    **majorType=ALL 조회 시 응답 필드**
                    - sections.majors[].majorName / majorType: 전공명 / MAIN(본전공) | DOUBLE(복수전공).
                    - sections.majors[].conditions: 해당 전공의 이수구분 조건 목록.
                    - sections.majors[].graduationRequired: 졸업필수 요건이 있는 전공만 non-null. 없는 전공은 null.
                    - sections.ge.conditions: REQUIRED_GE·DISTRIBUTED_GE·FREE_GE·SW_CERT_COURSE.
                    - sections.others.conditions: GENERAL_ELECTIVE 단일 조건.

                    **graduationRequired 필드 상세**
                    - hasGraduationRequired: 이 객체가 존재하면 항상 true.
                    - satisfied: 졸업필수 전체 충족 여부.
                    - totalCredit: 연결 과목 중 이수한 학점 합계.
                    - unmetDescriptions: 학점 기준 하위조건 미충족 문구 목록. 과목수 기준 조건은 미포함(과목 카드로만 표시).
                    - items[].name / current / required / unit / satisfied: 하위 요건별(예: 전문실기·맨손체조) 진행 현황. 플래너·노드뷰용.

                    **DISTRIBUTED_GE satisfied 판정**
                    - 24학번 이상: 학점 충족 AND 5개 영역 중 3개 이상 이수를 모두 만족해야 true.
                    - 19~23학번: 학점 기준만 적용.

                    **source=PLANNED**
                    - 플래너의 미이수·미수강 계획 과목을 스냅샷에 합산한 예상 졸업현황.
                    - graduationRequired도 계획 과목을 반영한다(예: 전문실기를 플래너에 담으면 items의 current가 올라감).
                    - 플래너가 없거나 신규 계획 과목이 없으면 COMPLETED와 동일.
                    - 이수구분 미지정 계획 과목은 GENERAL_ELECTIVE로 처리된다.
                    """
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
                                          "enrollmentStatus": "재학 중"
                                        },
                                        "graduatable": false,
                                        "curriculumSatisfied": false,
                                        "conditions": null,
                                        "graduationRequired": null,
                                        "sections": {
                                          "majors": [
                                            {
                                              "majorName": "스포츠의학과",
                                              "majorType": "MAIN",
                                              "conditions": [
                                                { "code": "MAJOR_BASIC",    "name": "전공기초", "current": 6,  "required": 7,  "unit": "CREDITS", "satisfied": false, "chartTarget": true  },
                                                { "code": "MAJOR_REQUIRED", "name": "전공필수", "current": 9,  "required": 9,  "unit": "CREDITS", "satisfied": true,  "chartTarget": true  },
                                                { "code": "MAJOR_ELECTIVE", "name": "전공선택", "current": 15, "required": 51, "unit": "CREDITS", "satisfied": false, "chartTarget": true  },
                                                { "code": "ENGLISH_COURSE",  "name": "영어강의",    "current": 7, "required": 3, "unit": "COURSES", "satisfied": true,  "chartTarget": false },
                                                { "code": "SW_CERT_COURSE",  "name": "SW인증강의", "current": 6, "required": 6, "unit": "CREDITS", "satisfied": true,  "chartTarget": false }
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
                                                { "code": "MAJOR_BASIC",    "name": "전공기초", "current": 3,  "required": 9,  "unit": "CREDITS", "satisfied": false, "chartTarget": true  },
                                                { "code": "MAJOR_REQUIRED", "name": "전공필수", "current": 6,  "required": 15, "unit": "CREDITS", "satisfied": false, "chartTarget": true  },
                                                { "code": "MAJOR_ELECTIVE", "name": "전공선택", "current": 6,  "required": 12, "unit": "CREDITS", "satisfied": false, "chartTarget": true  },
                                                { "code": "ENGLISH_COURSE",  "name": "영어강의",    "current": 0, "required": 3, "unit": "COURSES", "satisfied": false, "chartTarget": false },
                                                { "code": "SW_CERT_COURSE",  "name": "SW인증강의", "current": 0, "required": 0, "unit": "CREDITS", "satisfied": true,  "chartTarget": false }
                                              ],
                                              "graduationRequired": null
                                            },
                                            {
                                              "majorName": "화학공학과",
                                              "majorType": "DOUBLE",
                                              "conditions": [
                                                { "code": "MAJOR_BASIC",    "name": "전공기초", "current": 0,  "required": 9,  "unit": "CREDITS", "satisfied": false, "chartTarget": true  },
                                                { "code": "MAJOR_REQUIRED", "name": "전공필수", "current": 0,  "required": 15, "unit": "CREDITS", "satisfied": false, "chartTarget": true  },
                                                { "code": "MAJOR_ELECTIVE", "name": "전공선택", "current": 0,  "required": 12, "unit": "CREDITS", "satisfied": false, "chartTarget": true  },
                                                { "code": "ENGLISH_COURSE",  "name": "영어강의",    "current": 0, "required": 3, "unit": "COURSES", "satisfied": false, "chartTarget": false },
                                                { "code": "SW_CERT_COURSE",  "name": "SW인증강의", "current": 0, "required": 0, "unit": "CREDITS", "satisfied": true,  "chartTarget": false }
                                              ],
                                              "graduationRequired": null
                                            }
                                          ],
                                          "ge": {
                                            "majorName": null,
                                            "majorType": null,
                                            "conditions": [
                                              { "code": "REQUIRED_GE",    "name": "필수교과",      "current": 12, "required": 17, "unit": "CREDITS", "satisfied": false, "chartTarget": true  },
                                              { "code": "DISTRIBUTED_GE", "name": "배분이수교과", "current": 3,  "required": 9,  "unit": "CREDITS", "satisfied": false, "chartTarget": true  },
                                              { "code": "FREE_GE",        "name": "자유이수교과", "current": 5,  "required": 3,  "unit": "CREDITS", "satisfied": true,  "chartTarget": true  },
                                              { "code": "SW_CERT_COURSE",  "name": "SW인증강의", "current": 6, "required": 6, "unit": "CREDITS", "satisfied": true,  "chartTarget": false }
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
                                          "enrollmentStatus": "재학 중"
                                        },
                                        "graduatable": false,
                                        "curriculumSatisfied": false,
                                        "conditions": [
                                          { "code": "MAJOR_BASIC",    "name": "전공기초", "current": 9,  "required": 12, "unit": "CREDITS", "satisfied": false, "chartTarget": true  },
                                          { "code": "MAJOR_REQUIRED", "name": "전공필수", "current": 15, "required": 30, "unit": "CREDITS", "satisfied": false, "chartTarget": true  },
                                          { "code": "MAJOR_ELECTIVE", "name": "전공선택", "current": 6,  "required": 21, "unit": "CREDITS", "satisfied": false, "chartTarget": true  },
                                          { "code": "ENGLISH_COURSE",  "name": "영어강의",    "current": 1, "required": 3, "unit": "COURSES", "satisfied": false, "chartTarget": false },
                                          { "code": "SW_CERT_COURSE",  "name": "SW인증강의", "current": 3, "required": 6, "unit": "CREDITS", "satisfied": false, "chartTarget": false }
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

                    **전공 조회 방식**
                    - studentMajorId 있음: 그 전공 하나만 majors 배열에 담아 반환.
                    - studentMajorId 없음: majorType으로 조회. ALL(보유 전공 전부) / GE·OTHERS(본전공 스냅샷 기준).
                    - ENGLISH_COURSE·SW_CERT_COURSE + ALL: 탭·학과 구분 없이 전체 합산. majors 1개, majorType=null.
                    - ENGLISH_COURSE·SW_CERT_COURSE + GE: 해당 탭 이수구분에 속하는 과목만 반환.
                    - ENGLISH_COURSE·SW_CERT_COURSE + OTHERS: 기타 섹션에 조건이 없어 majors=[] 빈 응답.

                    **응답 필드**
                    - majors[].majorType: MAIN(본전공) | DOUBLE(복수전공). ENGLISH/SW + ALL 조회 시 null.
                    - majors[].current / required: 현재 이수량 / 요구량. GRADUATION_REQUIRED는 충족한/전체 하위조건 수.
                    - majors[].satisfied: 요건 충족 여부.
                    - majors[].hasRequiredList: true이면 courses에 미이수 필수과목(taken=false) 포함.
                    - majors[].unmetDescriptions: 학점 기준 미충족 하위조건 문구. GRADUATION_REQUIRED 전용. 과목수 기준은 미포함.
                    - majors[].distAreaDescriptions: 배분이수 완료 영역 안내 문구 목록. DISTRIBUTED_GE + 24학번 이상일 때만 채워짐. 완료 영역마다 한 항목 (예: '[생명, 우주, 인간]영역 이수 완료'). 그 외 빈 리스트.
                    - majors[].courses[].studentCourseId: 이수 기록 PK. taken=false(미이수)이면 null.
                    - majors[].courses[].taken: true(이수 완료) | false(미이수 필수과목).
                    - majors[].courses[].semester: 이수과목은 이수 학기, 미이수과목은 개설 학기. 정보 없으면 null.
                    - majors[].courses[].area: 배분이수 영역 정보(칩 표시용). DISTRIBUTED_GE + 영역 정보 있는 과목만 채워짐. 그 외 null.

                    **DISTRIBUTED_GE**
                    - 24학번 이상: distAreaDescriptions 채워짐(완료 영역마다 한 항목). satisfied는 학점 충족 AND 3개 이상 영역 이수 모두 필요.
                    - 19~23학번: distAreaDescriptions=[] 빈 리스트. satisfied는 학점 기준만 적용.

                    **GRADUATION_REQUIRED**
                    - 학과 독립 졸업요건에 연결된 과목을 이수/미이수 하나의 리스트로 반환.
                    - current / required: 충족한 하위조건 수 / 전체 하위조건 수.
                    - unmetDescriptions: 학점 기준 하위조건 미충족 문구만 포함. 과목수 기준 조건은 과목 카드로만 표시.
                    - 해당 학과가 아니면 결과가 비어 있다(hasRequiredList=false).
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공 (REQ_200_2)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = GraduationCourseResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "MAJOR_REQUIRED",
                                            summary = "전공필수 — 미이수 필수과목 포함, distAreaDescriptions=[]",
                                            value = """
                                    {
                                      "success": true,
                                      "code": "REQ_200_2",
                                      "message": "이수구분별 과목을 조회했습니다.",
                                      "data": {
                                        "conditionCode": "MAJOR_REQUIRED",
                                        "conditionName": "전공필수",
                                        "majors": [
                                          {
                                            "majorType": "MAIN",
                                            "departmentName": "화학공학과",
                                            "current": 9,
                                            "required": 15,
                                            "satisfied": false,
                                            "hasRequiredList": true,
                                            "unmetDescriptions": [],
                                            "distAreaDescriptions": [],
                                            "courses": [
                                              {
                                                "studentCourseId": 101,
                                                "name": "화공열역학1",
                                                "divisionCode": "MAJOR_REQUIRED",
                                                "divisionName": "전공필수",
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
                                                "divisionCode": "MAJOR_REQUIRED",
                                                "divisionName": "전공필수",
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
                                    """),
                                    @ExampleObject(
                                            name = "DISTRIBUTED_GE_24학번이상",
                                            summary = "배분이수 — 24학번 이상, 2개 영역 완료. distAreaDescriptions 채워짐, courses[].area 칩 포함",
                                            value = """
                                    {
                                      "success": true,
                                      "code": "REQ_200_2",
                                      "message": "이수구분별 과목을 조회했습니다.",
                                      "data": {
                                        "conditionCode": "DISTRIBUTED_GE",
                                        "conditionName": "배분이수교과",
                                        "majors": [
                                          {
                                            "majorType": "MAIN",
                                            "departmentName": "화학공학과",
                                            "current": 6,
                                            "required": 9,
                                            "satisfied": false,
                                            "hasRequiredList": false,
                                            "unmetDescriptions": [],
                                            "distAreaDescriptions": ["[생명, 우주, 인간]영역, [사회와 문화]영역 이수 완료"],
                                            "courses": [
                                              {
                                                "studentCourseId": 201,
                                                "name": "인간과 우주",
                                                "divisionCode": "DISTRIBUTED_GE",
                                                "divisionName": "배분이수교과",
                                                "departmentName": "교양학부",
                                                "credit": 3,
                                                "semester": "1학기",
                                                "taken": true,
                                                "isEnglish": false,
                                                "isSw": false,
                                                "area": { "code": "AREA_1", "name": "생명, 우주, 인간" }
                                              },
                                              {
                                                "studentCourseId": 202,
                                                "name": "현대사회의 이해",
                                                "divisionCode": "DISTRIBUTED_GE",
                                                "divisionName": "배분이수교과",
                                                "departmentName": "교양학부",
                                                "credit": 3,
                                                "semester": "2학기",
                                                "taken": true,
                                                "isEnglish": false,
                                                "isSw": false,
                                                "area": { "code": "AREA_2", "name": "사회와 문화" }
                                              }
                                            ]
                                          }
                                        ]
                                      }
                                    }
                                    """),
                                    @ExampleObject(
                                            name = "GRADUATION_REQUIRED",
                                            summary = "졸업필수 — 학점 미충족 조건 있음. unmetDescriptions 채워짐, distAreaDescriptions=[]",
                                            value = """
                                    {
                                      "success": true,
                                      "code": "REQ_200_2",
                                      "message": "이수구분별 과목을 조회했습니다.",
                                      "data": {
                                        "conditionCode": "GRADUATION_REQUIRED",
                                        "conditionName": "졸업필수",
                                        "majors": [
                                          {
                                            "majorType": "MAIN",
                                            "departmentName": "스포츠의학과",
                                            "current": 2,
                                            "required": 5,
                                            "satisfied": false,
                                            "hasRequiredList": true,
                                            "unmetDescriptions": ["[전문실기] 2/4학점 이수완료"],
                                            "distAreaDescriptions": [],
                                            "courses": [
                                              {
                                                "studentCourseId": 301,
                                                "name": "전문실기1",
                                                "divisionCode": "MAJOR_ELECTIVE",
                                                "divisionName": "전공선택",
                                                "departmentName": "스포츠의학과",
                                                "credit": 1,
                                                "semester": "1학기",
                                                "taken": true,
                                                "isEnglish": false,
                                                "isSw": false,
                                                "area": null
                                              },
                                              {
                                                "studentCourseId": null,
                                                "name": "전문실기2",
                                                "divisionCode": "MAJOR_ELECTIVE",
                                                "divisionName": "전공선택",
                                                "departmentName": "스포츠의학과",
                                                "credit": 1,
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
                            }
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