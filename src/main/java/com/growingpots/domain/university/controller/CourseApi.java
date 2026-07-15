package com.growingpots.domain.university.controller;

import com.growingpots.domain.university.dto.response.CourseSearchResponse;
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
@Tag(name = "Course", description = "과목 검색/조회 API")
public @interface CourseApi {

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @Operation(
            summary = "과목 검색/조회 (학기 플래너 - 과목 추가)",
            description = """
                    학기 플래너의 "과목 추가" 화면에서 쓰는 과목 검색/목록 조회 API입니다. 페이지 방식이 아니라
                    무한 스크롤을 전제로 하며, 필터를 아무것도 주지 않으면 전체 과목을 반환합니다.

                    - `keyword`: 과목명/학수번호 부분 일치 검색
                    - `divisionCategory`/`year`/`semester`/`credits`/`otherRequired`는 모두 다중 선택 가능한 배열입니다.
                      같은 필드 안의 값끼리는 OR(합집합), 서로 다른 필드끼리는 AND(교집합)로 결합합니다.
                      예: 이수영역=[전공필수, 전공기초], 학년=[2] → (전공필수 이거나 전공기초) 이면서 2학년인 과목
                    - `otherRequired=[SW, ENGLISH]` → SW인증 이거나 영어강의인 과목(OR). 다른 필터와는 AND로 결합됩니다.
                    - `divisionCategory=CROSS_MAJOR`(타전공인정과목)는 DIVISION 카테고리가 아니라 로그인한
                      학생의 소속 학과 기준으로 인정되는 과목을 별도로 조회합니다. 이 경우 `defaultDivisionName`도
                      과목의 원래 기본 이수구분이 아니라 학생 학과 기준으로 인정되는 이수구분명으로 표시됩니다.
                    - `semester=BOTH`(전체학기)가 포함되면 학기 필터 자체를 걸지 않은 것과 동일하게 전체 과목이
                      나옵니다. `semester=FIRST`/`SECOND`는 그 학기 전용 과목뿐 아니라 매 학기 개설되는(BOTH)
                      과목도 함께 포함합니다 - 그래서 `semester=[FIRST, SECOND]`(둘 다 선택)는 `semester=[BOTH]`와
                      결과가 동일합니다.
                    - `credits`에 4가 포함되면 "4학점 이상"(>=4)으로 처리하고, 나머지 값은 정확히 일치하는 값만 매칭합니다.
                    - `recommendedYearLow`/`recommendedYearHigh`가 다르면 권장 학년이 범위(예: 1~2학년)라는 뜻입니다.
                    - `inPlanner`는 학생의 현재 선택된 플래너 버전에 이 과목이 담겨 있으면 true입니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "과목 검색/조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CourseSearchResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "code": "UNIV_200_3",
                                      "message": "과목을 조회했습니다.",
                                      "data": {
                                        "courses": [
                                          {
                                            "courseId": 1,
                                            "courseCode": "CHE331",
                                            "name": "화공열역학1",
                                            "credit": 3,
                                            "departmentName": "화학공학과",
                                            "defaultDivisionName": "전공필수",
                                            "recommendedYearLow": 3,
                                            "recommendedYearHigh": 3,
                                            "openedSemester": "FIRST",
                                            "isEnglish": false,
                                            "isSw": false,
                                            "alreadyCompleted": false,
                                            "inPlanner": false
                                          }
                                        ],
                                        "page": { "page": 0, "size": 20, "totalElements": 137, "hasNext": true }
                                      }
                                    }
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 필터 값",
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
    @interface SearchCourses {}
}
