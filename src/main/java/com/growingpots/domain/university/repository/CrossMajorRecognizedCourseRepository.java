package com.growingpots.domain.university.repository;

import com.growingpots.domain.university.entity.CrossMajorRecognizedCourse;
import com.growingpots.domain.university.entity.Department;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CrossMajorRecognizedCourseRepository extends JpaRepository<CrossMajorRecognizedCourse, Long> {

    @Query("SELECT c FROM CrossMajorRecognizedCourse c "
            + "LEFT JOIN FETCH c.course LEFT JOIN FETCH c.recognizedDivision "
            + "WHERE c.targetDepartment = :targetDepartment")
    List<CrossMajorRecognizedCourse> findByTargetDepartment(@Param("targetDepartment") Department targetDepartment);
}
