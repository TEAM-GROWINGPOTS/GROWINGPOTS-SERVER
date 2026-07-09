package com.growingpots.domain.graduation.controller;

import io.swagger.v3.oas.annotations.Operation;
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
            description = "PDF 스냅샷 기반 졸업 현황을 반환한다. "
                    + "majorType=ALL: 본전공/복수전공/교양/기타 4개 섹션 분리(sections 필드, conditions=null). "
                    + "majorType=PRIMARY: 본전공 MAJOR_* 조건 + 전공 이수구분 영어/SW. "
                    + "majorType=MULTI: 복수전공 MAJOR_* 조건 + 전공 이수구분 영어/SW. "
                    + "majorType=GE: REQUIRED_GE/DISTRIBUTED_GE/FREE_GE + 교양 이수구분 영어/SW. "
                    + "majorType=OTHERS: GENERAL_ELECTIVE(기타) 조건 하나만 반환, 영어/SW 미포함. "
                    + "영어/SW 강의는 appliedDivision.category 기준으로 전공·교양 탭에만 배치된다. "
                    + "graduationRequired: PRIMARY/MULTI 탭(및 ALL의 sections.primary/multi)에서 항상 채워짐. "
                    + "GE/OTHERS 탭·ALL 탭(top-level)은 null. hasGraduationRequired로 해당 학과에 이수구분과 "
                    + "무관한 독립 졸업요건(예: 스포츠의학과 졸업필수)이 실제로 있는지 판단한다(false면 나머지 "
                    + "필드는 기본값이라 FE는 이 플래그로 탭 노출 여부만 보면 됨). "
                    + "totalCredit은 그 요건에 연결된 과목 중 이수한 학점 합계(source=PLANNED면 계획 과목 학점도 "
                    + "합산). unmetDescriptions는 학점 기준 하위조건만 문구로 담고(과목수 기준 조건은 과목 카드로만 "
                    + "표시), items는 전문실기·맨손체조 같은 하위 요건별 current/required/unit/satisfied를 "
                    + "구조화해서 담는다(플래너·노드뷰 화면용). "
                    + "source=PLANNED는 선택된 플래너 버전의 계획 과목 중 미이수/미수강 과목을 스냅샷에 합산한 예상 졸업현황을 반환한다. "
                    + "graduationRequired도 동일하게 계획 과목을 반영한다(예: 전문실기를 플래너에 담으면 items의 "
                    + "current가 올라감). 플래너가 없거나 신규 계획 과목이 없으면 COMPLETED와 동일한 응답. "
                    + "이수구분 미지정 계획 과목은 기타(GENERAL_ELECTIVE)로 처리된다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "졸업 현황 조회 성공 (REQ_200_1)"),
            @ApiResponse(responseCode = "401", description = "인증 실패 (CMN_005)"),
            @ApiResponse(responseCode = "404", description = "학적 정보 없음(USER_003) / 졸업 분석 데이터 없음(REQ_001)")
    })
    @interface GetGraduationStatus {
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @Operation(
            summary = "이수구분별 과목 조회",
            description = "divisionCode(예: MAJOR_REQUIRED)에 해당하는 이수/미이수 과목 목록을 반환한다. "
                    + "hasRequiredList=true인 경우 미이수 필수과목이 포함된다. "
                    + "majorType=ALL: 보유 전공 전부. PRIMARY: 본전공. MULTI: 복수전공. GE/OTHERS: 본전공 스냅샷 기준. "
                    + "ENGLISH_COURSE/SW_CERT_COURSE: majorType=ALL이면 탭·학과 구분 없이 전체 합산(majors 1개, majorType=null). "
                    + "PRIMARY/MULTI/GE이면 해당 탭의 이수구분(+전공 탭은 학과 기준)에 속하는 과목만 반환. "
                    + "OTHERS이면 영어·SW는 기타 섹션에 조건이 없으므로 majors=[] 빈 응답. "
                    + "GRADUATION_REQUIRED: 학과 자체의 독립 졸업요건(이수구분 무관, 예: 스포츠의학과 졸업필수) "
                    + "하위조건에 연결된 과목들을 이수/미이수 하나의 리스트로 합쳐 반환. current/required는 "
                    + "\"만족한 조건 수/전체 조건 수\"이고, unmetDescriptions에 학점 기준 하위조건의 미충족 문구만 "
                    + "담긴다(과목수 기준 조건은 과목 카드로만 표시하고 문구는 안 만듦). "
                    + "해당 학과가 아니면 결과가 비어 있다(hasRequiredList=false)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공 (REQ_200_2)"),
            @ApiResponse(responseCode = "400", description = "잘못된 divisionCode (CMN_002)"),
            @ApiResponse(responseCode = "401", description = "인증 실패 (CMN_005)"),
            @ApiResponse(responseCode = "404", description = "학적 정보 없음(USER_003) / 졸업 분석 데이터 없음(REQ_001) / 복수전공 없음(REQ_002)")
    })
    @interface GetDivisionCourses {
    }
}