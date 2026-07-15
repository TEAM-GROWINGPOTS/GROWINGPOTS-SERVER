package com.growingpots.domain.user.repository;

import com.growingpots.domain.user.entity.Member;
import com.growingpots.domain.user.entity.StudentProfile;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentProfileRepository extends JpaRepository<StudentProfile, Long> {

    boolean existsByMember(Member member);

    Optional<StudentProfile> findByMember(Member member);

    @Query("SELECT sp FROM StudentProfile sp JOIN FETCH sp.member JOIN FETCH sp.school JOIN FETCH sp.department WHERE sp.member.id = :memberId")
    Optional<StudentProfile> findWithDetailsByMemberId(@Param("memberId") Long memberId);

    // JOIN FETCH 없이 student_profile 단일 행만 잠근다. 컬렉션 join이 포함된 findWithDetailsByMemberId에
    // FOR UPDATE를 붙이면 조인된 행까지 모두 잠기므로, 락 전용 경량 쿼리를 별도로 둔다.
    // member_id 컬럼은 @OneToOne unique=true 제약으로 unique 인덱스가 자동 생성돼 단건 조회에 인덱스를 쓴다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT sp FROM StudentProfile sp WHERE sp.member.id = :memberId")
    Optional<StudentProfile> lockByMemberId(@Param("memberId") Long memberId);
}
