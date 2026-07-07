package com.growingpots.domain.university.repository;

import com.growingpots.domain.university.entity.CrossMajorRecognizedCourse;
import com.growingpots.domain.university.entity.Department;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CrossMajorRecognizedCourseRepository extends JpaRepository<CrossMajorRecognizedCourse, Long> {
    List<CrossMajorRecognizedCourse> findByTargetDepartment(Department targetDepartment);
}
