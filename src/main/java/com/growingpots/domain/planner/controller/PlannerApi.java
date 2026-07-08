package com.growingpots.domain.planner.controller;

import com.growingpots.domain.planner.dto.response.PlannerResponse;
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
                    completedTerms(이수완료/이수중, 조회전용)와 plannedTerms(계획, 편집대상)를 함께 반환한다.

                    - completedTerms는 STUDENT_COURSE를 (수강년도, 수강학기)로 그룹핑해 구성한다.
                      여름학기는 1학기, 겨울학기는 2학기 묶음에 합산된다. status가 IN_PROGRESS인
                      학기가 "이수 중", COMPLETED인 학기가 "이수 완료"다.
                    - completedTerms[].plannerTermVersionId는 실제 PLANNER_TERM_VERSION row가 없어
                      만든 합성 값이라 항상 음수다(plannedTerms 쪽 plannerTermVersionId는 실제 PK라
                      항상 양수). 다른 API에 넘기는 용도로 쓰면 안 된다.
                    - plannedTerms는 PLANNER_SIMULATION → PLANNER_TERM → PLANNER_TERM_VERSION →
                      PLANNER_VERSION_ITEM 트리를 그대로 반환한다. 한 번도 저장한 적 없는 학생은
                      빈 배열이 내려간다. isSelected=true인 버전이 노드뷰에 연결되는 폴더다.
                    - plannedTerms의 과목은 직접추가를 지원하지 않아 courseId가 항상 존재한다.
                    - divisionCategory/divisionName은 completedTerms에선 항상 값이 있지만,
                      plannedTerms에선 과목 자체에 기본 이수구분이 없으면 null일 수 있다.
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
                                            "termOrder": 3,
                                            "locked": false,
                                            "versions": [
                                              {
                                                "plannerTermVersionId": 4003,
                                                "versionNo": 1,
                                                "name": "폴더 1",
                                                "isSelected": true,
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
                                                    "positionOrder": 0
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
            description = "학기 플래너를 full-replace 방식으로 저장한다. "
                    + "plannerSimulationId가 null이면 새 플래너를 생성하고, 값이 있으면 기존 플래너를 전체 교체한다. "
                    + "각 학기(term)에는 정확히 1개의 isSelected=true 버전이 있어야 한다. "
                    + "1학기 개설 과목은 1학기 term에만, 2학기 개설 과목은 2학기 term에만 추가할 수 있다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "저장 성공 (PLAN_200_1)"),
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
            summary = "선택 버전(폴더) 변경",
            description = "지정한 학기(plannerTermId)의 선택 버전을 변경한다. "
                    + "한 트랜잭션 안에서 해당 학기의 모든 버전 isSelected=false → 지정 버전만 true로 전환한다. "
                    + "이미 선택된 버전을 다시 지정해도 200을 반환한다(멱등). "
                    + "다른 학기에 속한 versionId를 지정하면 404를 반환한다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "버전 변경 성공 (PLAN_200_4)"),
            @ApiResponse(responseCode = "400", description = "요청 형식 오류 (CMN_002) / 이수 완료 학기 (PLAN_006)"),
            @ApiResponse(responseCode = "401", description = "인증 실패 (CMN_005)"),
            @ApiResponse(responseCode = "404", description = "학적 정보 없음 (USER_003) / 학기·버전 없음 또는 소유 아님 (PLAN_005)")
    })
    @interface SelectVersion {
    }
}