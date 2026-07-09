package com.growingpots.domain.graduation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Schema(description = "이수구분별 과목 조회 응답")
@Getter
@Builder
public class GraduationCourseResponse {

    @Schema(description = "요청한 이수구분 코드 (요청값 그대로 반환)", example = "MAJOR_REQUIRED")
    private final String divisionCode;

    @Schema(description = "이수구분 이름 (예: 전공 필수, 영어 강의)", example = "전공 필수")
    private final String divisionName;

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

        @Schema(description = "요건 충족 여부")
        private final boolean satisfied;

        @Schema(description = "필수 과목 목록 존재 여부. "
                + "true이면 courses에 미이수 필수과목(taken=false)이 포함됨")
        private final boolean hasRequiredList;

        @Schema(description = "미충족 학점 기준 하위조건 안내 문구 목록. "
                + "GRADUATION_REQUIRED 전용. 그 외는 항상 빈 리스트. "
                + "과목수 기준 조건은 과목 카드로만 표시되어 이 목록에 포함되지 않음.")
        @Builder.Default
        private final List<String> unmetDescriptions = List.of();

        @Schema(description = "과목 목록. "
                + "이수과목(taken=true) + 미이수 필수과목(taken=false, hasRequiredList=true일 때). "
                + "이름순 정렬.")
        private final List<CourseInfo> courses;
    }

    @Schema(description = "개별 과목 정보")
    @Getter
    @Builder
    public static class CourseInfo {

        @Schema(description = "학생 이수 과목 PK. 미이수 과목(taken=false)이면 null", nullable = true)
        private final Long studentCourseId;

        @Schema(description = "과목명", example = "자료구조")
        private final String name;

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
    }
}