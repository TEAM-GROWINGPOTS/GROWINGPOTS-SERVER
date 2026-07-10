package com.growingpots.domain.planner.controller;

import com.growingpots.domain.planner.dto.response.PlannerResponse;
import com.growingpots.domain.planner.dto.response.PlannerSaveResponse;
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
                    - plannerTermVersionId: 실제 DB row 없이 만든 합성 값(항상 음수). 다른 API에 전달 불가.
                    - status: IN_PROGRESS(이수중) | COMPLETED(이수완료).
                    - courses[].studentCourseId: 이수 기록 PK.
                    - courses[].divisionCategory / divisionName: 이수구분 코드·표시명. 항상 존재.

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
                                                "credit": 3
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
                                                    "coursePositionOrder": 0
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
                    학기 플래너를 full-replace 방식으로 저장한다.

                    **저장 방식**
                    - plannerSimulationId가 null이면 기존 플래너를 찾아 쓰거나 신규 생성한다. 값이 있으면 해당 플래너를 전체 교체한다.
                    - 각 학기(term)에는 정확히 1개의 isSelected=true 버전이 있어야 한다.
                    - 학기 내 versionNo와 versionOrder는 각각 중복 불가.
                    - 1학기 개설 과목은 semester=1 term에만, 2학기 개설 과목은 semester=2 term에만 추가할 수 있다.
                    - 저장 후 terms는 yearLevel → semester 오름차순으로 정렬되어 반환된다.

                    **요청 주요 필드**
                    - plannerSimulationId: 플래너 PK. null이면 신규 생성 또는 기존 플래너 재사용.
                    - terms[].versions[].versionNo: 버전 번호(1부터 시작, 학기 내 중복 불가).
                    - terms[].versions[].versionOrder: 폴더 표시 순서(0-based, 학기 내 중복 불가).
                    - terms[].versions[].isSelected: 선택된 폴더 여부. 학기당 정확히 1개 true.
                    - terms[].versions[].items[].coursePositionOrder: 카드뷰 내 과목 순서(0-based).

                    **응답 주요 필드**
                    - plannerSimulationId: 생성·교체된 플래너 PK.
                    - terms[].plannerTermId: 생성된 학기 PK.
                    - terms[].versions[].plannerTermVersionId: 생성된 버전(폴더) PK.
                    - terms[].versions[].versionOrder: 저장된 폴더 표시 순서.
                    - terms[].versions[].items[].plannerVersionItemId: 생성된 과목 항목 PK.
                    - terms[].versions[].items[].coursePositionOrder: 저장된 과목 순서.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "저장 성공 (PLAN_200_1)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PlannerSaveResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "code": "PLAN_200_1",
                                      "message": "플래너를 저장했습니다.",
                                      "data": {
                                        "plannerSimulationId": 1001,
                                        "terms": [
                                          {
                                            "plannerTermId": 3001,
                                            "yearLevel": 2,
                                            "semester": 1,
                                            "versions": [
                                              {
                                                "plannerTermVersionId": 4001,
                                                "versionNo": 1,
                                                "isSelected": true,
                                                "versionOrder": 0,
                                                "items": [
                                                  { "plannerVersionItemId": 5001, "courseId": 78, "coursePositionOrder": 0 }
                                                ]
                                              },
                                              {
                                                "plannerTermVersionId": 4002,
                                                "versionNo": 2,
                                                "isSelected": false,
                                                "versionOrder": 1,
                                                "items": []
                                              }
                                            ]
                                          }
                                        ]
                                      }
                                    }
                                    """)
                    )),
            @ApiResponse(responseCode = "400", description = "데이터 정합성 오류 (PLAN_004)"),
            @ApiResponse(responseCode = "401", description = "인증 실패 (CMN_005)"),
            @ApiResponse(responseCode = "403", description = "플래너 접근 권한 없음 (PLAN_003)"),
            @ApiResponse(responseCode = "404", description = "학적 정보 없음 (USER_003) / 플래너 없음 (PLAN_002) / 과목 없음 (PLAN_001)")
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