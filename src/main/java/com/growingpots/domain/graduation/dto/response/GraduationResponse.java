package com.growingpots.domain.graduation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Schema(description = "졸업 현황 응답")
@Getter
@Builder
public class GraduationResponse {

    @Schema(description = "총 이수학점·평점·재학상태 요약")
    private final Summary summary;

    @Schema(description = "졸업 가능 여부. 학점·평점·비학점 인증 요건을 모두 충족한 경우 true")
    private final boolean graduatable;

    @Schema(description = "조건 목록. PRIMARY/MULTI/GE/OTHERS 탭 조회 시 채워짐. ALL 탭이면 null")
    private final List<ConditionInfo> conditions;

    @Schema(description = "학과 독립 졸업요건 요약. PRIMARY/MULTI 탭이고 해당 학과에 졸업필수 요건이 있을 때만 채워짐. 그 외 null")
    private final GraduationRequiredSummary graduationRequired;

    @Schema(description = "4섹션 분리 응답. ALL 탭 조회 시 채워짐. 그 외 null")
    private final AllSections sections;

    @Schema(description = "비학점 인증 결과 목록 (논문·영어·SW·TOPIK 등)")
    private final List<CertInfo> certs;

    @Schema(description = "총 이수학점·평점·재학상태 요약")
    @Getter
    @Builder
    public static class Summary {

        @Schema(description = "총 이수학점 현황")
        private final CreditInfo totalCredits;

        @Schema(description = "평점 현황")
        private final GpaInfo gpa;

        @Schema(description = "재학 상태 (PDF 원문 그대로, 예: 재학, 휴학)")
        private final String enrollmentStatus;
    }

    @Schema(description = "학점 현황")
    @Getter
    @AllArgsConstructor
    public static class CreditInfo {

        @Schema(description = "현재 이수 학점")
        private final int current;

        @Schema(description = "졸업 필요 학점")
        private final int required;
    }

    @Schema(description = "평점 현황")
    @Getter
    @AllArgsConstructor
    public static class GpaInfo {

        @Schema(description = "현재 누적 평점")
        private final BigDecimal current;

        @Schema(description = "졸업 최소 요구 평점. 평점 요건이 없는 학과는 null", nullable = true)
        private final BigDecimal min;
    }

    @Schema(description = "단일 졸업요건 조건 정보")
    @Getter
    @Builder
    public static class ConditionInfo {

        @Schema(
                description = """
                        조건 코드.
                        - 전공 탭: MAJOR_BASIC(전공 기초) · MAJOR_REQUIRED(전공 필수) · MAJOR_ELECTIVE(전공 선택)
                        - 교양 탭: REQUIRED_GE(필수 교과) · DISTRIBUTED_GE(배분 이수 교과) · FREE_GE(자유 이수 교과)
                        - 기타 탭: GENERAL_ELECTIVE
                        - 전공·교양 탭 공통: ENGLISH_COURSE(영어 강의) · SW_CERT_COURSE(SW 인증 강의)""",
                allowableValues = {
                        "MAJOR_BASIC", "MAJOR_REQUIRED", "MAJOR_ELECTIVE",
                        "REQUIRED_GE", "DISTRIBUTED_GE", "FREE_GE",
                        "GENERAL_ELECTIVE", "ENGLISH_COURSE", "SW_CERT_COURSE"
                })
        private final String code;

        @Schema(description = "조건 이름 (예: 전공 필수, 영어 강의)")
        private final String name;

        @Schema(description = "현재 이수량. unit=CREDITS이면 학점 합계, unit=COURSES이면 과목 수")
        private final int current;

        @Schema(description = "요구량. GENERAL_ELECTIVE는 졸업 기준이 없어 null", nullable = true)
        private final Integer required;

        @Schema(description = "단위", allowableValues = {"CREDITS", "COURSES"})
        private final String unit;

        @Schema(description = "요건 충족 여부")
        private final boolean satisfied;

        @Schema(description = "원형 차트 포함 여부. GENERAL_ELECTIVE는 false (차트 8등분에서 제외)")
        private final boolean chartTarget;
    }

    @Schema(description = "비학점 인증 결과")
    @Getter
    @AllArgsConstructor
    public static class CertInfo {

        @Schema(
                description = "인증 유형. "
                        + "THESIS(논문) · GRADUATION_CERT(졸업능력인정) · ENGLISH(영어인증) · "
                        + "SW(SW인증) · TOPIK(한국어능력시험)",
                allowableValues = {"THESIS", "ENGLISH", "SW", "TOPIK", "GRADUATION_CERT"})
        private final String certType;

        @Schema(
                description = "결과. "
                        + "PASS: 통과, FAIL: 미통과(졸업 불가 처리), EXEMPT: 면제, NONE: 해당없음(한국인 학생의 TOPIK 등)",
                allowableValues = {"PASS", "FAIL", "EXEMPT", "NONE"})
        private final String result;
    }

    @Schema(description = "ALL 탭 전용 4섹션 응답")
    @Getter
    @Builder
    public static class AllSections {

        @Schema(description = "본전공 섹션")
        private final TabSection primary;

        @Schema(description = "복수전공 섹션. 복수전공이 없으면 null", nullable = true)
        private final TabSection multi;

        @Schema(description = "교양 섹션")
        private final TabSection ge;

        @Schema(description = "기타(일반선택) 섹션")
        private final TabSection others;
    }

    @Schema(description = "섹션별 조건 묶음")
    @Getter
    @Builder
    public static class TabSection {

        @Schema(description = "전공명. primary/multi 섹션만 채워짐. ge/others는 null", nullable = true)
        private final String majorName;

        @Schema(description = "해당 섹션의 조건 목록")
        private final List<ConditionInfo> conditions;

        @Schema(description = "학과 독립 졸업요건 요약. primary/multi 섹션이고 해당 학과에 졸업필수 요건이 있을 때만 채워짐. 그 외 null", nullable = true)
        private final GraduationRequiredSummary graduationRequired;
    }

    @Schema(description = "학과 독립 졸업요건 요약 (이수구분 무관, 예: 스포츠의학과 졸업필수). "
            + "PRIMARY/MULTI 탭이면 항상 채워짐 — hasGraduationRequired로 해당 학과에 요건 자체가 "
            + "있는지 판단한다(false면 나머지 필드는 기본값이라 탭을 숨기면 됨).")
    @Getter
    @Builder
    public static class GraduationRequiredSummary {

        @Schema(description = "이 학과에 졸업필수 요건 자체가 있는지 여부 (예: 스포츠의학과만 true). "
                + "FE는 이 값으로 졸업필수 탭 노출 여부를 판단하면 된다.")
        private final boolean hasGraduationRequired;

        @Schema(description = "요건 충족 여부")
        private final boolean satisfied;

        @Schema(description = "연결 과목 중 이수(COMPLETED)한 학점 합계. source=PLANNED면 계획 과목 학점도 합산")
        private final int totalCredit;

        @Schema(description = "미충족 학점 기준 하위조건 안내 문구 목록 (예: '[졸업필수] 2/4학점 이수완료'). "
                + "과목수 기준 조건은 미포함 — 하위 요건별 수치는 items를 사용할 것")
        private final List<String> unmetDescriptions;

        @Schema(description = "하위 요건별(예: 전문실기, 맨손체조) 진행 현황. 플래너·노드뷰 화면에서 사용")
        private final List<RequirementProgress> items;
    }

    @Schema(description = "졸업필수 하위 요건 하나의 진행 현황 (예: 전문실기 2/2과목)")
    @Getter
    @Builder
    public static class RequirementProgress {

        @Schema(description = "하위 요건 이름", example = "전문실기")
        private final String name;

        @Schema(description = "현재 이수량. source=PLANNED면 계획 과목 포함")
        private final int current;

        @Schema(description = "요구량")
        private final int required;

        @Schema(description = "단위", allowableValues = {"CREDITS", "COURSES"})
        private final String unit;

        @Schema(description = "이 하위 요건 충족 여부")
        private final boolean satisfied;
    }
}