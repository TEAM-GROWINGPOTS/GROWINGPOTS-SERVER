package com.growingpots.domain.user.repository;

import com.growingpots.domain.university.entity.Department;
import com.growingpots.domain.user.entity.StudentMajor;
import com.growingpots.domain.user.entity.StudentProfile;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentMajorRepository extends JpaRepository<StudentMajor, Long> {
    Optional<StudentMajor> findByStudentProfileAndDepartment(StudentProfile studentProfile, Department department);
}
