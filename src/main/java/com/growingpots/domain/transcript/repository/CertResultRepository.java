package com.growingpots.domain.transcript.repository;

import com.growingpots.domain.transcript.entity.CertResult;
import com.growingpots.domain.transcript.entity.enums.RecordSource;
import com.growingpots.domain.user.entity.StudentProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CertResultRepository extends JpaRepository<CertResult, Long> {
    void deleteByStudentProfileAndSource(StudentProfile studentProfile, RecordSource source);
}
