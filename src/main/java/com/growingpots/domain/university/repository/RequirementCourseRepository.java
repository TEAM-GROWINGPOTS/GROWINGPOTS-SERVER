package com.growingpots.domain.university.repository;

import com.growingpots.domain.university.entity.Department;
import com.growingpots.domain.university.entity.Division;
import com.growingpots.domain.university.entity.RequirementCourse;
import com.growingpots.domain.university.entity.Track;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RequirementCourseRepository extends JpaRepository<RequirementCourse, Long> {

    // baseYear <= 입학년도인 요건 중 트랙 무관(track IS NULL) 또는 학생 트랙과 일치하는 것만 조회
    @Query("SELECT rc FROM RequirementCourse rc WHERE rc.department = :department "
            + "AND rc.division = :division AND rc.baseYear <= :admissionYear "
            + "AND (rc.track IS NULL OR rc.track = :track)")
    List<RequirementCourse> findApplicable(
            @Param("department") Department department,
            @Param("division") Division division,
            @Param("admissionYear") int admissionYear,
            @Param("track") Track track);
}