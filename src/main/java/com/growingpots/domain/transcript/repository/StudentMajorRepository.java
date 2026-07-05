package com.growingpots.domain.transcript.repository;

import com.growingpots.domain.transcript.entity.enums.MajorType;
import com.growingpots.domain.transcript.entity.StudentMajor;
import com.growingpots.domain.university.entity.Department;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentMajorRepository extends JpaRepository<StudentMajor, Long> {
    Optional<StudentMajor> findByMemberIdAndDepartment(Long memberId, Department department);

    // DEPARTMENT와 매칭되지 않은 전공(department=null)을 위한 조회
    Optional<StudentMajor> findByMemberIdAndDepartmentIsNullAndMajorType(Long memberId, MajorType majorType);
}
