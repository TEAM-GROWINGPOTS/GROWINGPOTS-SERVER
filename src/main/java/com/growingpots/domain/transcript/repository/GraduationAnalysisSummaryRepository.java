package com.growingpots.domain.transcript.repository;

import com.growingpots.domain.transcript.entity.GraduationAnalysisSummary;
import com.growingpots.domain.user.entity.StudentMajor;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GraduationAnalysisSummaryRepository extends JpaRepository<GraduationAnalysisSummary, Long> {
    Optional<GraduationAnalysisSummary> findByStudentMajor(StudentMajor studentMajor);
}
