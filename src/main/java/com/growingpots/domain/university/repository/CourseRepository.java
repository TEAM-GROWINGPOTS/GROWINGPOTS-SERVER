package com.growingpots.domain.university.repository;

import com.growingpots.domain.university.entity.Course;
import com.growingpots.domain.university.entity.School;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CourseRepository extends JpaRepository<Course, Long>, JpaSpecificationExecutor<Course> {
    Optional<Course> findByCourseCode(String courseCode);
    List<Course> findBySchool(School school);
}
