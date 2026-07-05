package com.growingpots.domain.user.repository;

import com.growingpots.domain.user.entity.Member;
import com.growingpots.domain.user.entity.StudentProfile;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentProfileRepository extends JpaRepository<StudentProfile, Long> {
    boolean existsByMember(Member member);

    Optional<StudentProfile> findByMember(Member member);
}
