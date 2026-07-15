package com.growingpots.domain.university.service;

import com.growingpots.domain.university.dto.response.OnboardingOptionsResponse;
import com.growingpots.domain.university.entity.Department;
import com.growingpots.domain.university.entity.School;
import com.growingpots.domain.university.repository.DepartmentRepository;
import com.growingpots.domain.university.repository.SchoolRepository;
import com.growingpots.global.exception.BaseException;
import com.growingpots.global.response.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class OnboardingService {

    private static final int ADMISSION_YEAR_RANGE = 8;
    private static final String GENERAL_EDUCATION_COLLEGE = "후마니타스칼리지";

    private final SchoolRepository schoolRepository;
    private final DepartmentRepository departmentRepository;

    @Transactional(readOnly = true)
    public OnboardingOptionsResponse getOptions(Long schoolId) {
        List<School> schools;
        List<Department> departments;

        if (schoolId != null) {
            School school = schoolRepository.findById(schoolId)
                    .orElseThrow(() -> new BaseException(ErrorCode.UNIVERSITY_NOT_FOUND));
            schools = List.of(school);
            departments = departmentRepository.findBySchoolId(schoolId);
        } else {
            schools = schoolRepository.findAll();
            departments = departmentRepository.findAll();
        }

        return new OnboardingOptionsResponse(
                schools.stream().map(OnboardingOptionsResponse.SchoolInfo::from).toList(),
                departments.stream()
                        .filter(d -> !GENERAL_EDUCATION_COLLEGE.equals(d.getCollege()))
                        .map(OnboardingOptionsResponse.DepartmentInfo::from).toList(),
                buildAdmissionYears()
        );
    }

    private List<Integer> buildAdmissionYears() {
        int currentYear = LocalDate.now().getYear();
        return IntStream.iterate(currentYear, y -> y - 1)
                .limit(ADMISSION_YEAR_RANGE)
                .boxed()
                .toList();
    }
}