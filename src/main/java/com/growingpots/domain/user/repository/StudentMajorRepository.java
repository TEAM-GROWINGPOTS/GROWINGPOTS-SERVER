package com.growingpots.domain.user.repository;

import com.growingpots.domain.university.entity.Department;
import com.growingpots.domain.user.entity.StudentMajor;
import com.growingpots.domain.user.entity.StudentProfile;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface StudentMajorRepository extends JpaRepository<StudentMajor, Long> {
    Optional<StudentMajor> findByStudentProfileAndDepartment(StudentProfile studentProfile, Department department);

    @Query("SELECT sm FROM StudentMajor sm JOIN FETCH sm.department LEFT JOIN FETCH sm.track WHERE sm.studentProfile = :studentProfile")
    List<StudentMajor> findWithDepartmentByStudentProfile(@Param("studentProfile") StudentProfile studentProfile);
}
