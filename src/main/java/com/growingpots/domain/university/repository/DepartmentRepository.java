package com.growingpots.domain.university.repository;

import com.growingpots.domain.university.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DepartmentRepository extends JpaRepository<Department, Long> {
    List<Department> findBySchoolId(Long schoolId);

    Optional<Department> findBySchoolIdAndName(Long schoolId, String name);
}