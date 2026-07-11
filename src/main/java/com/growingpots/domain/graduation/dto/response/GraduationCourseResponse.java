package com.growingpots.domain.graduation.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Schema(description = "졸업 조건별 과목 조회 응답")
@Getter
@Builder
public class GraduationCourseResponse {

    @Schema(description = "조회한 졸업 조건 코드 (요청값 그대로 반환). "
            + "이수구분(MAJOR_REQUIRED 등) 외에 GRADUATION_REQUIRED·ENGLISH_COURSE·SW_CERT_COURSE 포함",
            example = "MAJOR_REQUIRED")
    private final String conditionCode;

    @Schema(description = "졸업 조건 이름", example = "전공 필수")
    private final String conditionName;

    @Schema(description = "전공별 과목 목록. "
            + "ENGLISH_COURSE·SW_CERT_COURSE + majorType=ALL이면 단일 항목(majorType=null). "
            + "ENGLISH_COURSE·SW_CERT_COURSE + majorType=OTHERS이면 빈 리스트.")
    private final List<MajorCourses> majors;

    @Schema(description = "전공별 과목 및 이수 현황")
    @Getter
    @Builder
    public static class MajorCourses {

        @Schema(
                description = "전공 유형. "
                        + "MAIN: 본전공, DOUBLE: 복수전공. "
                        + "ENGLISH_COURSE·SW_CERT_COURSE + majorType=ALL 조회 시 null (전체 합산 항목)",
                allowableValues = {"MAIN", "DOUBLE"},
                nullable = true)
        private final String majorType;

        @Schema(description = "학과명. ENGLISH_COURSE·SW_CERT_COURSE + majorType=ALL 조회 시 null", nullable = true)
        private final String departmentName;

        @Schema(description = "현재 이수량. "
                + "일반 이수구분: 학점 합계(CREDITS) 또는 과목 수(COURSES). "
                + "GRADUATION_REQUIRED: 충족한 하위조건 수")
        private final int current;

        @Schema(description = "요구량. "
                + "GENERAL_ELECTIVE는 졸업 기준이 없어 null. "
                + "GRADUATION_REQUIRED: 전체 하위조건 수",
                nullable = true)
        private final Integer required;

        @Schema(description = "요건 충족 여부. DISTRIBUTED_GE는 학점 충족 AND 영역 m-of-n 충족 모두 필요")
        private final boolean satisfied;

        @Schema(description = "필수 과목 목록 존재 여부. "
                + "true이면 courses에 미이수 필수과목(taken=false)이 포함됨")
        private final boolean hasRequiredList;

        @Schema(description = "미충족 학점 기준 하위조건 안내 문구 목록. "
                + "GRADUATION_REQUIRED 전용. 그 외는 항상 빈 리스트. "
                + "과목수 기준 조건은 과목 카드로만 표시되어 이 목록에 포함되지 않음.")
        @Builder.Default
        private final List<String> unmetDescriptions = List.of();

        @Schema(description = "배분이수 완료 영역 안내 문구. "
                + "DISTRIBUTED_GE + 24학번 이상 + 완료 영역 1개 이상일 때 단일 항목 리스트로 채워짐 (예: ['[생명, 우주, 인간]영역, [사회와 문화]영역 이수 완료']). "
                + "19~23학번이거나 완료 영역 없거나 다른 이수구분이면 빈 리스트.")
        @Builder.Default
        private final List<String> distAreaDescriptions = List.of();

        @Schema(description = "과목 목록. "
                + "이수과목(taken=true) + 미이수 필수과목(taken=false, hasRequiredList=true일 때). "
                + "이름순 정렬.")
        private final List<CourseInfo> courses;
    }

    @Schema(description = "교양 영역 기본 정보 (과목카드 영역 칩 표시용)")
    @Getter
    @AllArgsConstructor
    public static class AreaInfo {

        @Schema(description = "영역 코드", example = "AREA_1")
        private final String code;

        @Schema(description = "영역명", example = "생명, 우주, 인간")
        private final String name;
    }

    @Schema(description = "개별 과목 정보")
    @Getter
    @Builder
    public static class CourseInfo {

        @Schema(description = "학생 이수 과목 PK. 미이수 과목(taken=false)이면 null", nullable = true)
        private final Long studentCourseId;

        @Schema(description = "과목명", example = "자료구조")
        private final String name;

        @Schema(description = "이수구분 코드. "
                + "이수 과목(taken=true): 학생 성적표에 적용된 이수구분(appliedDivision). 매칭 실패 시 null. "
                + "미이수 과목(taken=false): 과목의 기본 이수구분(defaultDivision). 미설정 시 null.",
                example = "MAJOR_REQUIRED", nullable = true)
        private final String divisionCode;

        @Schema(description = "이수구분 이름. divisionCode가 null이면 null",
                example = "전공필수", nullable = true)
        private final String divisionName;

        @Schema(description = "개설 학과명. 정보가 없으면 null", nullable = true)
        private final String departmentName;

        @Schema(description = "학점", example = "3")
        private final int credit;

        @Schema(description = "학기. "
                + "이수과목(taken=true): 이수한 학기 (1학기 · 2학기 · 여름학기 · 겨울학기). "
                + "미이수과목(taken=false): 개설 학기 (1학기 · 2학기 · 1·2학기). "
                + "정보가 없으면 null",
                nullable = true)
        private final String semester;

        @Schema(description = "이수 여부. true: 이수 완료, false: 미이수 필수과목")
        private final boolean taken;

        // Lombok이 boolean isEnglish/isSw에 대해 isEnglish()/isSw() 게터를 만드는데, Jackson은
        // "is" 접두사를 벗겨 "english"/"sw"로 직렬화해버린다. 게터에 @JsonProperty를 얹어 이름 고정.
        @Schema(description = "영어 강의 여부")
        @Getter(onMethod_ = @__(@JsonProperty("isEnglish")))
        private final boolean isEnglish;

        @Schema(description = "SW 인증 강의 여부")
        @Getter(onMethod_ = @__(@JsonProperty("isSw")))
        private final boolean isSw;

        @Schema(description = "배분이수 영역 정보 (과목카드 영역 칩용). "
                + "DISTRIBUTED_GE 과목이고 Course 매칭 및 영역 정보가 있을 때만 채워짐, 그 외 null",
                nullable = true)
        private final AreaInfo area;
    }
}