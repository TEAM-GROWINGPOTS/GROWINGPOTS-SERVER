package com.growingpots.domain.user.dto.request;

import com.growingpots.domain.transcript.entity.enums.Semester;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Schema(description = "이수 과목 수정 요청")
public record StudentCourseUpdateRequest(
        @Schema(description = "과목 목록 (요청에 없는 기존 과목은 삭제됨)") @NotEmpty @Valid List<CourseUpdateItem> courses
) {
    // studentCourseId가 null이면 신규 추가(직접 추가한 과목), 있으면 기존 항목 수정.
    // 요청에 없는 기존 studentCourseId는 편집 화면에서 삭제된 것으로 간주해 함께 삭제된다.
    @Schema(description = "개별 과목 수정 항목")
    public record CourseUpdateItem(
            @Schema(description = "이수 과목 PK. null이면 직접 추가한 신규 과목으로 처리", nullable = true) Long studentCourseId,
            // 기존 과목(studentCourseId 있음)은 GET 응답의 courseId를 그대로 넣어서 보내야 매칭이 유지된다.
            // null을 보내면 기존에 매칭돼 있던 과목이라도 매칭이 풀려버리니(#214), GET에서 courseId가
            // null이 아니었던 과목은 반드시 그 값을 그대로 echo해서 보낼 것 - "안 건드림"과 "명시적으로
            // 매칭 해제"를 구분할 방법이 없어서, 프론트가 값을 모르면(옛날 GET 응답 캐시 등) 절대 null로
            // 덮어쓰면 안 된다.
            @Schema(description = "과목 마스터 PK. 매칭된 과목이 있으면 GET 응답의 courseId를 그대로 넣어야 "
                    + "매칭이 유지됨 - null로 보내면 기존 매칭도 풀림. 직접 추가한 신규 과목이라 매칭이 "
                    + "없으면 null", nullable = true) Long courseId,
            @Schema(description = "과목명 (PDF 원문 또는 직접 입력)") @NotBlank String rawCourseName,
            @Schema(description = "개설 학과 PK. 없으면 null", nullable = true) Long departmentId,
            @Schema(description = "학점") @NotNull Integer credit,
            @Schema(description = "적용 이수구분 PK. 없으면 null", nullable = true) Long appliedDivisionId,
            @Schema(description = "이수 연도", example = "2023", nullable = true) Integer takenYear,
            @Schema(description = "이수 학기", allowableValues = {"FIRST", "SUMMER", "SECOND", "WINTER"}, nullable = true)
            Semester takenSemester
    ) {
    }
}
