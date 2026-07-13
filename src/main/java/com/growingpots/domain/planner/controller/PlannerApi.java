package com.growingpots.domain.planner.controller;

import com.growingpots.domain.graduation.dto.response.GraduationResponse;
import com.growingpots.domain.planner.dto.response.PlannerResponse;
import com.growingpots.domain.planner.dto.response.PrerequisiteCheckResponse;
import com.growingpots.domain.planner.dto.response.SelectVersionResponse;
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
@Tag(name = "Planner", description = "학기 플래너 API")
public @interface PlannerApi {

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @Operation(
            summary = "학기 플래너 전체 조회 (카드뷰/노드뷰)",
            description = """
                    completedTerms(이수완료·이수중)와 plannedTerms(계획 학기)를 함께 반환한다.

                    **completedTerms 응답 필드**
                    - yearLevel / semester: 수강 순서 기준으로 산정한 학년·학기 (1-based).
                    - plannerTermVersionId: completedTerms는 조회전용이라 실제 PLANNER_TERM_VERSION row가
                      없어 만든 합성 값이다. 진짜 PK는 AUTO_INCREMENT라 항상 양수이므로 절대 안 겹치도록
                      항상 음수로 만든다 - 실수로 이 값을 진짜 PK인 것처럼 다른 API(예: 버전 선택)에 넘기더라도
                      DB에 존재할 수 없는 값이라 엉뚱한 데 매칭되지 않고 즉시 404로 실패하게 하기 위한
                      안전장치다. 다른 API에 절대 전달하면 안 된다.
                    - status: IN_PROGRESS(이수중) | COMPLETED(이수완료).
                    - courses[].studentCourseId: 이수 기록 PK.
                    - courses[].divisionCategory / divisionName: 이수구분 코드·표시명. 항상 존재.
                    - courses[].isEnglish / isSw: 영어강의·SW인증강의 여부. course 매칭 안 된 과목은 false.

                    **completedTerms 구성 방식**
                    STUDENT_COURSE를 (수강년도, 수강학기)로 그룹핑한다.
                    여름학기는 1학기, 겨울학기는 2학기 묶음에 합산된다.

                    **plannedTerms 응답 필드**
                    - plannerTermId: 학기 PK.
                    - versions[].plannerTermVersionId: 버전(폴더) PK.
                    - versions[].versionNo: 버전 번호(1부터 시작).
                    - versions[].versionOrder: 폴더 표시 순서(0-based).
                    - versions[].isSelected: true인 버전이 노드뷰에 연결되는 폴더.
                    - versions[].totalCredit: 해당 버전에 담긴 과목의 총 학점.
                    - versions[].courses[].plannerVersionItemId: 과목 항목 PK.
                    - versions[].courses[].coursePositionOrder: 카드뷰 내 과목 순서(0-based).
                    - versions[].courses[].divisionCategory / divisionName: 기본 이수구분이 없는 과목은 null.
                    - versions[].courses[].courseId: 직접추가를 지원하지 않아 항상 존재.
                    - versions[].courses[].isEnglish / isSw: 영어강의·SW인증강의 여부.

                    **plannedTerms 구성 방식**
                    PLANNER_SIMULATION → PLANNER_TERM → PLANNER_TERM_VERSION → PLANNER_VERSION_ITEM 트리 구조.
                    한 번도 저장한 적 없는 학생은 빈 배열이 내려간다.
                    학기 목록은 yearLevel → semester 오름차순으로 정렬된다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "플래너 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PlannerResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "code": "PLAN_200",
                                      "message": "플래너 조회에 성공했습니다.",
                                      "data": {
                                        "completedTerms": [
                                          {
                                            "yearLevel": 1,
                                            "semester": 1,
                                            "plannerTermVersionId": -11,
                                            "name": "1학년 1학기",
                                            "status": "COMPLETED",
                                            "totalCredit": 3,
                                            "courses": [
                                              {
                                                "studentCourseId": 7001,
                                                "courseId": 12,
                                                "courseName": "미디어와사회",
                                                "departmentName": "미디어학과",
                                                "divisionCategory": "MAJOR_REQUIRED",
                                                "divisionName": "전공필수",
                                                "recommendedYearLow": 1,
                                                "recommendedYearHigh": 1,
                                                "openedSemester": "FIRST",
                                                "credit": 3,
                                                "isEnglish": false,
                                                "isSw": false
                                              }
                                            ]
                                          }
                                        ],
                                        "plannedTerms": [
                                          {
                                            "plannerTermId": 3003,
                                            "yearLevel": 2,
                                            "semester": 1,
                                            "versions": [
                                              {
                                                "plannerTermVersionId": 4003,
                                                "versionNo": 1,
                                                "name": "폴더 1",
                                                "isSelected": true,
                                                "versionOrder": 0,
                                                "totalCredit": 3,
                                                "courses": [
                                                  {
                                                    "plannerVersionItemId": 5002,
                                                    "courseId": 78,
                                                    "courseName": "경영정보시스템",
                                                    "departmentName": "산업경영공학과",
                                                    "divisionCategory": "MAJOR_REQUIRED",
                                                    "divisionName": "전공필수",
                                                    "recommendedYearLow": 2,
                                                    "recommendedYearHigh": 2,
                                                    "openedSemester": "FIRST",
                                                    "credit": 3,
                                                    "coursePositionOrder": 0,
                                                    "isEnglish": false,
                                                    "isSw": false
                                                  }
                                                ]
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
                    description = "학적 정보 없음(온보딩 미완료)",
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
    @interface GetPlanner {
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @Operation(
            summary = "학기 플래너 저장",
            description = """
                    학기 플래너를 full-replace 방식으로 저장하고, 저장이 반영된 졸업현황(source=PLANNED, majorType=ALL)을 반환한다.

                    **저장 방식**
                    - plannerSimulationId가 null이면 기존 플래너를 찾아 쓰거나 신규 생성한다. 값이 있으면 해당 플래너를 전체 교체한다.
                    - 각 학기(term)에는 정확히 1개의 isSelected=true 버전이 있어야 한다.
                    - 학기 내 versionNo와 versionOrder는 각각 중복 불가.
                    - 과목의 개설 학기와 무관하게 모든 학기 term에 추가할 수 있다.

                    **성공 응답 (200)**
                    - data: 방금 저장된 플래너가 반영된 졸업현황 (GET /students/me/graduation?majorType=ALL&source=PLANNED 와 동일한 스키마).
                    - 졸업현황 계산이 일시적으로 실패한 경우 data는 null이 될 수 있다. 이 경우 별도로 GET /students/me/graduation을 호출한다.

                    **실패 응답 (4xx) — PLAN_004 · PLAN_001 · PLAN_002**
                    - success: false, code: 에러 코드.
                    - data: 저장 전 상태의 졸업현황 (롤백된 서버 상태 기준). 클라이언트가 별도 GET 없이 이전 상태로 UI를 복원할 수 있다.
                    - data가 null인 경우 졸업현황 계산 자체가 불가한 상황이므로 별도 GET을 호출한다.

                    **실패 응답 (4xx) — USER_003 · PLAN_003**
                    - 인증·프로필 오류: data는 항상 null.

                    **@Valid 검증 실패 (400, CMN_002)**
                    - 요청 바디 자체가 잘못된 경우(예: terms 누락). data는 null. 이전 상태가 필요하면 별도 GET을 호출한다.

                    **요청 주요 필드**
                    - plannerSimulationId: 플래너 PK. null이면 신규 생성 또는 기존 플래너 재사용.
                    - terms[].versions[].versionNo: 버전 번호(1부터 시작, 학기 내 중복 불가).
                    - terms[].versions[].versionOrder: 폴더 표시 순서(0-based, 학기 내 중복 불가).
                    - terms[].versions[].isSelected: 선택된 폴더 여부. 학기당 정확히 1개 true.
                    - terms[].versions[].items[].coursePositionOrder: 카드뷰 내 과목 순서(0-based).
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "저장 성공 — 저장이 반영된 졸업현황 반환 (PLAN_200_1)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = GraduationResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "code": "PLAN_200_1",
                                      "message": "플래너를 저장했습니다.",
                                      "data": {
                                        "summary": {
                                          "totalCredits": { "current": 98, "required": 130 },
                                          "gpa": { "current": 3.85, "min": 2.0 },
                                          "enrollmentStatus": "재학"
                                        },
                                        "graduatable": false,
                                        "conditions": null,
                                        "sections": {
                                          "majors": [
                                            {
                                              "majorName": "컴퓨터공학과",
                                              "majorType": "MAIN",
                                              "conditions": [],
                                              "graduationRequired": null
                                            }
                                          ],
                                          "ge": { "majorName": null, "majorType": null, "conditions": [] },
                                          "others": { "majorName": null, "majorType": null, "conditions": [] }
                                        },
                                        "certs": []
                                      }
                                    }
                                    """)
                    )),
            @ApiResponse(
                    responseCode = "400",
                    description = "데이터 정합성 오류 — data에 저장 전 졸업현황 포함 (PLAN_004). data가 null이면 졸업현황 계산 불가 상태이므로 GET /students/me/graduation 별도 호출",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = GraduationResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "code": "PLAN_004",
                                      "message": "플래너 데이터 정합성 오류입니다.",
                                      "data": {
                                        "summary": {
                                          "totalCredits": { "current": 92, "required": 130 },
                                          "gpa": { "current": 3.85, "min": 2.0 },
                                          "enrollmentStatus": "재학"
                                        },
                                        "graduatable": false,
                                        "conditions": null,
                                        "sections": {
                                          "majors": [
                                            {
                                              "majorName": "컴퓨터공학과",
                                              "majorType": "MAIN",
                                              "conditions": [],
                                              "graduationRequired": null
                                            }
                                          ],
                                          "ge": { "majorName": null, "majorType": null, "conditions": [] },
                                          "others": { "majorName": null, "majorType": null, "conditions": [] }
                                        },
                                        "certs": []
                                      }
                                    }
                                    """)
                    )),
            @ApiResponse(responseCode = "401", description = "인증 실패 (CMN_005)"),
            @ApiResponse(responseCode = "403", description = "플래너 접근 권한 없음 — data: null (PLAN_003)"),
            @ApiResponse(
                    responseCode = "404",
                    description = "학적 정보 없음 — data: null (USER_003) / 플래너 없음 — data에 저장 전 졸업현황 포함 (PLAN_002) / 과목 없음 — data에 저장 전 졸업현황 포함 (PLAN_001)")
    })
    @interface SavePlanner {
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @Operation(
            summary = "선수과목 검사",
            description = """
                    요청한 courseId 목록에 대해 미이수 선수과목을 반환한다.

                    **규칙**
                    - COMPLETED·IN_PROGRESS 상태 과목은 이수한 것으로 간주한다.
                    - 학과 특정 선수과목 규칙이 공통(null) 규칙보다 우선 적용된다.
                    - 선수과목이 없거나 모두 이수한 과목은 results에 포함되지 않는다.

                    **응답 주요 필드**
                    - results[].courseId: 선수과목 미이수 상태인 검사 대상 과목 PK.
                    - results[].missingPrerequisites[].type: REQUIRED(필수 선수과목) | RECOMMENDED(권장 선수과목).
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "검사 성공 (PLAN_200_2)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PrerequisiteCheckResponse.class),
                            examples = {
                                    @ExampleObject(name = "미이수 선수과목 있음", value = """
                                            {
                                              "success": true,
                                              "code": "PLAN_200_2",
                                              "message": "선수과목 검사 결과입니다.",
                                              "data": {
                                                "results": [
                                                  {
                                                    "courseId": 78,
                                                    "courseName": "데이터베이스",
                                                    "missingPrerequisites": [
                                                      { "courseId": 45, "courseName": "자료구조", "type": "REQUIRED" }
                                                    ]
                                                  }
                                                ]
                                              }
                                            }
                                            """),
                                    @ExampleObject(name = "모두 이수 완료 (선수과목 없음)", value = """
                                            {
                                              "success": true,
                                              "code": "PLAN_200_2",
                                              "message": "선수과목 검사 결과입니다.",
                                              "data": {
                                                "results": []
                                              }
                                            }
                                            """)
                            }
                    )),
            @ApiResponse(responseCode = "400", description = "요청 형식 오류 (CMN_002)"),
            @ApiResponse(responseCode = "401", description = "인증 실패 (CMN_005)"),
            @ApiResponse(responseCode = "404", description = "학적 정보 없음 (USER_003)")
    })
    @interface CheckPrerequisites {
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @Operation(
            summary = "선택 버전(폴더) 변경",
            description = """
                    지정한 학기(plannerTermId)의 선택 버전(폴더)을 변경한다.

                    **동작**
                    - 한 트랜잭션에서 해당 학기의 모든 버전 isSelected=false → 지정 버전만 true로 전환한다.
                    - 이미 선택된 버전을 다시 지정해도 200을 반환한다(멱등).
                    - 다른 학기에 속한 plannerTermVersionId를 지정하면 404를 반환한다.

                    **요청 필드**
                    - plannerTermVersionId: 선택할 버전(폴더) PK.

                    **응답 필드**
                    - plannerTermId: 변경된 학기 PK.
                    - selectedVersionId: 선택된 버전 PK.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "버전 변경 성공 (PLAN_200_4)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = SelectVersionResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "code": "PLAN_200_4",
                                      "message": "선택 버전이 변경되었습니다.",
                                      "data": {
                                        "plannerTermId": 3001,
                                        "selectedVersionId": 4002
                                      }
                                    }
                                    """)
                    )),
            @ApiResponse(responseCode = "400", description = "요청 형식 오류 (CMN_002) / 이수 완료 학기 (PLAN_006)"),
            @ApiResponse(responseCode = "401", description = "인증 실패 (CMN_005)"),
            @ApiResponse(responseCode = "404", description = "학적 정보 없음 (USER_003) / 학기·버전 없음 또는 소유 아님 (PLAN_005)")
    })
    @interface SelectVersion {
    }
}