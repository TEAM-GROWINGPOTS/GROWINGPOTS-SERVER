package com.growingpots.domain.university.dto.response;

import com.growingpots.domain.university.entity.Department;
import com.growingpots.domain.university.entity.School;

import java.util.List;

public record OnboardingOptionsResponse(
        List<SchoolInfo> schools,
        List<DepartmentInfo> departments,
        List<Integer> admissionYears
) {
    public record SchoolInfo(Long schoolId, String name) {
        public static SchoolInfo from(School school) {
            return new SchoolInfo(school.getId(), school.getName());
        }
    }

    public record DepartmentInfo(Long departmentId, Long schoolId, String college, String name) {
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