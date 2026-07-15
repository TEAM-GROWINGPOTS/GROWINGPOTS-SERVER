package com.growingpots.domain.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Schema(description = "이수 과목 목록 응답")
@Getter
@Builder
public class StudentCourseListResponse {

    @Schema(description = "이수 과목 목록")
    private final List<CourseInfo> courses;

    @Schema(description = "이수구분 선택 옵션 목록. 학교마다 보유한 이수구분만 포함되며 "
            + "전공기초→전공필수→전공선택→필수교과→배분이수교과→자유이수교과→일반선택 순으로 정렬. "
            + "검수 화면 이수구분 드롭다운에 사용하고, 선택한 항목의 id를 PUT 요청 appliedDivisionId에 넣으면 됨.")
    private final List<DivisionInfo> availableDivisions;

    @Schema(description = "이수구분 옵션 정보")
    @Getter
    @Builder
    public static class DivisionInfo {
        @Schema(description = "이수구분 PK. PUT /students/me/courses 요청의 appliedDivisionId에 사용", example = "2")
        private final Long id;
        @Schema(description = "이수구분 표시명", example = "전공필수")
        private final String name;
    }

    @Schema(description = "개별 이수 과목 정보")
    @Getter
    @Builder
    public static class CourseInfo {

        @Schema(description = "이수 과목 PK")
        private final Long studentCourseId;

        @Schema(description = "학수번호", example = "THE2001")
        private final String courseCode;

        @Schema(description = "매칭된 과목 마스터(COURSE) PK. 과목 마스터와 매칭된 경우에만 값이 있고, 매칭 안 "
                + "된 과목(직접 추가했거나 마스터에 없는 학수번호)은 null — 이 경우엔 애초에 매칭된 과목이 없는 "
                + "상태라, 수정 PUT 요청에 null을 그대로 넣어도 매칭이 지워지지 않음. 값이 있으면 그대로 PUT의 "
                + "courseId에 넣어야 기존 매칭이 유지됨(안 넣고 null을 보내면 매칭이 풀림)", example = "12", nullable = true)
        private final Long courseId;

        @Schema(description = "과목명", example = "연극문헌과연기")
        private final String name;

        @Schema(description = "개설 학과명. 과목 마스터와 매칭됐거나 직접 지정된 경우에 값이 있음. 교양 과목인데 매칭된 "
                + "학과가 없으면 학교에 대체 학과가 지정돼 있을 때 그 이름(예: 후마니타스칼리지)이, 없으면 \"교양\"이 대신 채워짐. "
                + "그 외(전공인데 매칭 안 된 직접 추가 과목 등)는 null", nullable = true)
        private final String departmentName;

        @Schema(description = "개설 학과 PK. departmentName의 근거가 된 학과 ID(직접 지정했거나 과목 마스터와 매칭됐거나, "
                + "학교의 교양 대체 학과로 채워진 경우). departmentName이 표시용 \"교양\" 문자열(학교에 대체 학과가 아직 "
                + "없는 경우)이거나 departmentName 자체가 null이면 departmentId도 null — 이 경우엔 애초에 연결된 학과가 "
                + "없는 상태라, 수정 PUT 요청에 null을 그대로 넣어도 데이터가 지워지지 않음. 값이 있으면 그대로 PUT의 "
                + "departmentId에 넣으면 됨", nullable = true)
        private final Long departmentId;

        @Schema(description = "학점", example = "3")
        private final int credit;

        @Schema(description = "적용 이수구분명. appliedDivision이 지정된 과목(전공/교양 무관)만 값이 있음. 지정 안 된 과목은 null", nullable = true)
        private final String appliedDivisionName;

        @Schema(description = "적용 이수구분 PK. appliedDivisionName의 근거가 된 이수구분 ID. appliedDivisionName이 null이면 "
                + "(애초에 지정된 이수구분이 없다는 뜻) appliedDivisionId도 null — 이 경우 수정 PUT 요청에 null을 그대로 "
                + "넣어도 데이터가 지워지지 않음. 값이 있으면 그대로 PUT의 appliedDivisionId에 넣으면 됨", nullable = true)
        private final Long appliedDivisionId;

        @Schema(description = "이수 연도", example = "2023", nullable = true)
        private final Integer takenYear;

        @Schema(description = "이수 학기 (1학기 · 여름학기 · 2학기 · 겨울학기)", example = "1학기", nullable = true)
        private final String takenSemester;
    }
}