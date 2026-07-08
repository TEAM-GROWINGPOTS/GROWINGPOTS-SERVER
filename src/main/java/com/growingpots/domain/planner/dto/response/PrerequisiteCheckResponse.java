package com.growingpots.domain.planner.dto.response;

import com.growingpots.domain.university.entity.enums.PrerequisiteType;
import java.util.List;

public record PrerequisiteCheckResponse(List<CourseResult> results) {

    public record CourseResult(
            Long courseId,
            String courseName,
            List<MissingPrerequisite> missingPrerequisites
    ) {
    }

    public record MissingPrerequisite(
            Long courseId,
            String courseName,
            PrerequisiteType type
    ) {
    }
}