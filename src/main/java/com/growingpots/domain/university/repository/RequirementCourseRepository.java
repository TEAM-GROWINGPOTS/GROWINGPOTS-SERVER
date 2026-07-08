package com.growingpots.domain.university.repository;

import com.growingpots.domain.university.entity.Department;
import com.growingpots.domain.university.entity.Division;
import com.growingpots.domain.university.entity.RequirementCourse;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RequirementCourseRepository extends JpaRepository<RequirementCourse, Long> {

    @Query("SELECT rc FROM RequirementCourse rc WHERE rc.department = :department "
            + "AND rc.division = :division AND rc.baseYear <= :admissionYear")
    List<RequirementCourse> findApplicable(
            @Param("department") Department department,
            @Param("division") Division division,
            @Param("admissionYear") int admissionYear);
}