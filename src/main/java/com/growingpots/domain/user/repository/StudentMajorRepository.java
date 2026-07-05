package com.growingpots.domain.user.repository;

import com.growingpots.domain.user.entity.StudentMajor;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentMajorRepository extends JpaRepository<StudentMajor, Long> {
}