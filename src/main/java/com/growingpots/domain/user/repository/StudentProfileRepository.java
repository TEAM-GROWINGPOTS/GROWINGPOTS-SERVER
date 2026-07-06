package com.growingpots.domain.user.repository;

import com.growingpots.domain.user.entity.Member;
import com.growingpots.domain.user.entity.StudentProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface StudentProfileRepository extends JpaRepository<StudentProfile, Long> {

    boolean existsByMember(Member member);

    @Query("SELECT sp FROM StudentProfile sp JOIN FETCH sp.member JOIN FETCH sp.school JOIN FETCH sp.department WHERE sp.member.id = :memberId")
    Optional<StudentProfile> findWithDetailsByMemberId(@Param("memberId") Long memberId);
}
