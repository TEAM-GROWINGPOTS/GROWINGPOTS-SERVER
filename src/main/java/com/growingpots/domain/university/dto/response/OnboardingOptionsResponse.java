package com.growingpots.domain.university.dto.response;

import com.growingpots.domain.university.entity.Department;
import com.growingpots.domain.university.entity.School;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "온보딩 옵션 응답")
public record OnboardingOptionsResponse(
        @Schema(description = "학교 목록") List<SchoolInfo> schools,
        @Schema(description = "학과 목록") List<DepartmentInfo> departments,
        @Schema(description = "입학연도 목록 (현재 연도 기준 최근 8개년, 내림차순)") List<Integer> admissionYears
) {
    @Schema(description = "학교 정보")
    public record SchoolInfo(
            @Schema(description = "학교 PK") Long schoolId,
            @Schema(description = "학교명", example = "경희대학교 국제캠퍼스") String name
    ) {
        public static SchoolInfo from(School school) {
            return new SchoolInfo(school.getId(), school.getName());
        }
    }

    @Schema(description = "학과 정보")
    public record DepartmentInfo(
            @Schema(description = "학과 PK") Long departmentId,
            @Schema(description = "학교 PK") Long schoolId,
            @Schema(description = "단과대학명", example = "공과대학") String college,
            @Schema(description = "학과명", example = "컴퓨터공학과") String name
    ) {
        public static DepartmentInfo from(Department department) {
            return new DepartmentInfo(
                    department.getId(),
                    department.getSchool().getId(),
                    department.getCollege(),
                    department.getName()
            );
        }
    }
}