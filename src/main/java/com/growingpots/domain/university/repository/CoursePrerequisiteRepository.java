package com.growingpots.domain.university.repository;

import com.growingpots.domain.university.entity.CoursePrerequisite;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CoursePrerequisiteRepository extends JpaRepository<CoursePrerequisite, Long> {

    @Query("SELECT cp FROM CoursePrerequisite cp "
            + "JOIN FETCH cp.course c "
            + "JOIN FETCH cp.requiredCourse rc "
            + "LEFT JOIN FETCH cp.department d "
            + "WHERE c.id IN :courseIds "
            + "AND (d IS NULL OR d.id = :departmentId)")
    List<CoursePrerequisite> findByCourseIdsAndDepartment(
            @Param("courseIds") List<Long> courseIds,
            @Param("departmentId") Long departmentId);
}