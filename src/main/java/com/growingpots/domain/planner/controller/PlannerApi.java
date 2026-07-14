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
                    - versions[].courses[].retakeDisplay: 재수강 표시 유형. 일반 과목은 null이며 JSON 응답에서 필드 자체가 생략된다.
                      - BADGE: 이미 이수완료(COMPLETED) 또는 이수중(IN_PROGRESS)인 과목이 플래너 여러 학기에 담겨 있을 때, 가장 최신 학기(yearLevel → semester 기준) 항목
                      - DIMMED: 재수강 과목 중 BADGE가 아닌 이전 학기 항목
                      - null(필드 생략): 재수강이 아닌 일반 과목

                    **재수강 표시 계산 방식**
                    서버가 GET 응답 시점에 저장된 전체 플래너 기준으로 매번 재계산하는 read-only 파생 값이다.
                    PUT(저장) 요청 바디에 포함하지 않으며, 포함해도 무시된다.
                    동일 과목의 재수강 인스턴스가 1개뿐이면 그 항목이 BADGE(DIMMED 없음).
                    최신 학기 판정 기준: yearLevel 큰 쪽 우선, 같으면 semester 큰 쪽(2학기 > 1학기).

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
                            examples = @ExampleObject(
                                    name = "재수강 표시 포함 예시",
                                    summary = "미디어와사회(이수완료)를 2-1·2-2에 모두 담은 경우: 2-1=DIMMED, 2-2=BADGE. 경영정보시스템은 신규 과목이라 retakeDisplay 필드 생략.",
                                    value = """
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
                                                "name": "미디어와사회",
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
                                                "totalCredit": 6,
                                                "courses": [
                                                  {
                                                    "plannerVersionItemId": 5002,
                                                    "courseId": 12,
                                                    "name": "미디어와사회",
                                                    "departmentName": "미디어학과",
                                                    "divisionCategory": "MAJOR_REQUIRED",
                                                    "divisionName": "전공필수",
                                                    "recommendedYearLow": 1,
                                                    "recommendedYearHigh": 1,
                                                    "openedSemester": "FIRST",
                                                    "credit": 3,
                                                    "coursePositionOrder": 0,
                                                    "isEnglish": false,
                                                    "isSw": false,
                                                    "retakeDisplay": "DIMMED"
                                                  },
                                                  {
                                                    "plannerVersionItemId": 5003,
                                                    "courseId": 78,
                                                    "name": "경영정보시스템",
                                                    "departmentName": "산업경영공학과",
                                                    "divisionCategory": "MAJOR_REQUIRED",
                                                    "divisionName": "전공필수",
                                                    "recommendedYearLow": 2,
                                                    "recommendedYearHigh": 2,
                                                    "openedSemester": "FIRST",
                                                    "credit": 3,
                                                    "coursePositionOrder": 1,
                                                    "isEnglish": false,
                                                    "isSw": false
                                                  }
                                                ]
                                              }
                                            ]
                                          },
                                          {
                                            "plannerTermId": 3004,
                                            "yearLevel": 2,
                                            "semester": 2,
                                            "versions": [
                                              {
                                                "plannerTermVersionId": 4004,
                                                "versionNo": 1,
                                                "name": "폴더 1",
                                                "isSelected": true,
                                                "versionOrder": 0,
                                                "totalCredit": 3,
                                                "courses": [
                                                  {
                                                    "plannerVersionItemId": 5004,
                                                    "courseId": 12,
                                                    "name": "미디어와사회",
                                                    "departmentName": "미디어학과",
                                                    "divisionCategory": "MAJOR_REQUIRED",
                                                    "divisionName": "전공필수",
                                                    "recommendedYearLow": 1,
                                                    "recommendedYearHigh": 1,
                                                    "openedSemester": "FIRST",
                                                    "credit": 3,
                                                    "coursePositionOrder": 0,
                                                    "isEnglish": false,
                                                    "isSw": false,
                                                    "retakeDisplay": "BADGE"
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
                    - 저장 후 terms는 yearLevel → semester 오름차순으로 정렬되어 반환된다.
                    - terms를 빈 배열([])로 보내면 기존에 저장된 학기를 전부 삭제한다(계획 전체 비우기).

                    **성공 응답 (200)**
                    - data: 방금 저장된 플래너가 반영된 졸업현황 (GET /students/me/graduation?majorType=ALL&source=PLANNED 와 동일한 스키마).
                    - 졸업현황 계산이 일시적으로 실패한 경우 data는 null이 될 수 있다. 이 경우 별도로 GET /students/me/graduation을 호출한다.

                    **실패 응답 (4xx) — PLAN_004 · PLAN_001 · PLAN_002**
                    - success: false, code: 에러 코드.
                    - data: 저장 전 상태의 플래너 (롤백된 서버 상태 기준, GET /api/v1/planner 와 동일한 스키마). 클라이언트가 별도 GET 없이 이전 상태로 UI를 복원할 수 있다.
                    - data가 null인 경우 플래너 조회 자체가 불가한 상황이므로 별도 GET을 호출한다.

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
                                        "graduationRequired": null,
                                        "sections": {
                                          "majors": [
                                            {
                                              "majorName": "컴퓨터공학과",
                                              "majorType": "MAIN",
                                              "conditions": [],
                                              "graduationRequired": null
                                            }
                                          ],
                                          "ge": { "majorName": null, "majorType": null, "conditions": [], "graduationRequired": null },
                                          "others": { "majorName": null, "majorType": null, "conditions": [], "graduationRequired": null }
                                        },
                                        "certs": []
                                      }
                                    }
                                    """)
                    )),
            @ApiResponse(
                    responseCode = "400",
                    description = "데이터 정합성 오류 — data에 저장 전 플래너 포함 (PLAN_004). data가 null이면 플래너 조회 불가 상태이므로 GET /api/v1/planner 별도 호출",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PlannerResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "code": "PLAN_004",
                                      "message": "플래너 데이터 정합성 오류입니다.",
                                      "data": {
                                        "completedTerms": [],
                                        "plannedTerms": [
                                          {
                                            "plannerTermId": 3001,
                                            "yearLevel": 1,
                                            "semester": 1,
                                            "versions": [
                                              {
                                                "plannerTermVersionId": 4001,
                                                "versionNo": 1,
                                                "name": "폴더 1",
                                                "isSelected": true,
                                                "versionOrder": 0,
                                                "totalCredit": 3,
                                                "courses": [
                                                  {
                                                    "plannerVersionItemId": 5001,
                                                    "courseId": 12,
                                                    "name": "미디어와사회",
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
                    )),
            @ApiResponse(responseCode = "401", description = "인증 실패 (CMN_005)"),
            @ApiResponse(responseCode = "403", description = "플래너 접근 권한 없음 — data: null (PLAN_003)"),
            @ApiResponse(
                    responseCode = "404",
                    description = "학적 정보 없음 — data: null (USER_003) / 플래너 없음 — data에 저장 전 플래너 포함 (PLAN_002) / 과목 없음 — data에 저장 전 플래너 포함 (PLAN_001)")
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
                                                    "name": "데이터베이스",
                                                    "missingPrerequisites": [
                                                      { "courseId": 45, "name": "자료구조", "type": "REQUIRED" }
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