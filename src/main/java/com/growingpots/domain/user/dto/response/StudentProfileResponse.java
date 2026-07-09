package com.growingpots.domain.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Schema(description = "학생 프로필 응답")
@Getter
@Builder
public class StudentProfileResponse {

    @Schema(description = "학생 프로필 PK")
    private final Long studentProfileId;

    @Schema(description = "학생 이름")
    private final String name;

    @Schema(description = "학교명")
    private final String schoolName;

    @Schema(description = "학과명 (본전공 기준)")
    private final String departmentName;

    @Schema(description = "학번. PDF 미업로드 시 null", nullable = true)
    private final String studentNo;

    @Schema(description = "입학연도", example = "2023")
    private final int admissionYear;

    @Schema(description = "현재 학년. PDF 미업로드 시 null", nullable = true)
    private final Integer gradeLevel;

    @Schema(description = "현재 학기. PDF 미업로드 시 null", nullable = true)
    private final Integer semester;

    @Schema(description = "재학 상태 (예: 재학, 휴학). PDF 미업로드 시 null", nullable = true)
    private final String enrollmentStatus;

    @Schema(description = "보유 전공 목록")
    private final List<MajorInfo> majors;

    @Schema(description = "전공 정보")
    @Getter
    @Builder
    public static class MajorInfo {

        @Schema(description = "학생 전공 PK")
        private final Long studentMajorId;

        @Schema(description = "전공 유형", allowableValues = {"MAIN", "DOUBLE"})
        private final String majorType;

        @Schema(description = "학과명", example = "컴퓨터공학과")
        private final String departmentName;

        @Schema(description = "트랙명. 없으면 null", nullable = true)
        private final String trackName;
    }
}