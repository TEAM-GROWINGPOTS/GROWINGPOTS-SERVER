package com.growingpots.domain.user.repository;

import com.growingpots.domain.user.entity.Member;
import com.growingpots.domain.user.entity.StudentProfile;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentProfileRepository extends JpaRepository<StudentProfile, Long> {

    Optional<StudentProfile> findByMember(Member member);

    @Query("SELECT sp FROM StudentProfile sp JOIN FETCH sp.member JOIN FETCH sp.school JOIN FETCH sp.department WHERE sp.member.id = :memberId")
    Optional<StudentProfile> findWithDetailsByMemberId(@Param("memberId") Long memberId);

    // 온보딩 완료 여부 - 분석확인 화면에서 "확인"을 누른 회원만 true.
    boolean existsByMemberAndOnboardingConfirmedAtIsNotNull(Member member);
}
