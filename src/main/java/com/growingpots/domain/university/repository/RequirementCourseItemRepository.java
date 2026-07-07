package com.growingpots.domain.university.repository;

import com.growingpots.domain.university.entity.RequirementCourse;
import com.growingpots.domain.university.entity.RequirementCourseItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RequirementCourseItemRepository extends JpaRepository<RequirementCourseItem, Long> {

    @Query("SELECT rci FROM RequirementCourseItem rci JOIN FETCH rci.course c LEFT JOIN FETCH c.offeringDepartment "
            + "WHERE rci.requirementCourse IN :requirementCourses")
    List<RequirementCourseItem> findWithCourseByRequirementCourseIn(
            @Param("requirementCourses") List<RequirementCourse> requirementCourses);
}