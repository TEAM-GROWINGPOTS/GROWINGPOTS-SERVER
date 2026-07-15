package com.growingpots.domain.transcript.repository;

import com.growingpots.domain.transcript.entity.GraduationAnalysisSummary;
import com.growingpots.domain.user.entity.Member;
import com.growingpots.domain.user.entity.StudentMajor;
import com.growingpots.domain.user.entity.StudentProfile;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GraduationAnalysisSummaryRepository extends JpaRepository<GraduationAnalysisSummary, Long> {
    Optional<GraduationAnalysisSummary> findByStudentMajor(StudentMajor studentMajor);

    void deleteByStudentMajorIn(List<StudentMajor> studentMajors);

    // PDF를 한 번이라도 분석했는지(=진짜 온보딩 완료인지) 판단하는 용도(#221). StudentProfile이
    // 있어도 PDF 분석 전이면(GraduationAnalysisSummary 없음) 아직 온보딩 완료가 아니라고 본다.
    boolean existsByStudentMajor_StudentProfile(StudentProfile studentProfile);

    boolean existsByStudentMajor_StudentProfile_Member(Member member);
}
