package com.growingpots.domain.planner.dto.response;

import com.growingpots.domain.university.entity.enums.PrerequisiteType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "선수과목 검사 응답")
public record PrerequisiteCheckResponse(
        @Schema(description = "검사 결과 목록. 미이수 선수과목이 있는 과목만 포함") List<CourseResult> results
) {

    @Schema(description = "과목별 검사 결과")
    public record CourseResult(
            @Schema(description = "검사 대상 과목 PK") Long courseId,
            @Schema(description = "검사 대상 과목명") String courseName,
            @Schema(description = "미이수 선수과목 목록") List<MissingPrerequisite> missingPrerequisites
    ) {
    }

    @Schema(description = "미이수 선수과목 정보")
    public record MissingPrerequisite(
            @Schema(description = "선수과목 PK") Long courseId,
            @Schema(description = "선수과목명") String courseName,
            @Schema(description = "선수과목 유형. REQUIRED: 필수 선수과목, RECOMMENDED: 권장 선수과목",
                    allowableValues = {"REQUIRED", "RECOMMENDED"})
            PrerequisiteType type
    ) {
    }
}