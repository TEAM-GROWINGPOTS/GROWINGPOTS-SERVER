package com.growingpots.domain.user.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class StudentProfileResponse {

    private final Long studentProfileId;
    private final String name;
    private final String schoolName;
    private final String departmentName;
    private final String studentNo;
    private final int admissionYear;
    private final Integer gradeLevel;
    private final Integer semester;
    private final String enrollmentStatus;
    private final List<MajorInfo> majors;

    @Getter
    @Builder
    public static class MajorInfo {
        private final Long studentMajorId;
        private final String majorType;
        private final String departmentName;
        private final String trackName;
    }
}