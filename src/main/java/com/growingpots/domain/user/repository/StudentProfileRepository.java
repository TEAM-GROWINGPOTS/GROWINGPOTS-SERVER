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

    Optional<StudentProfile> findByMember(Member member);

    @Query("SELECT sp FROM StudentProfile sp JOIN FETCH sp.member JOIN FETCH sp.school JOIN FETCH sp.department WHERE sp.member.id = :memberId")
    Optional<StudentProfile> findWithDetailsByMemberId(@Param("memberId") Long memberId);

    // 온보딩 완료 여부 - 분석확인 화면에서 "확인"을 누른 회원만 true.
    boolean existsByMemberAndOnboardingConfirmedAtIsNotNull(Member member);

    // student_profile 단일 행만 잠그는 경량 락 전용 쿼리.
    // 스칼라 프로젝션(sp.id)으로 반환해 엔티티를 1차 캐시에 올리지 않는다 — 엔티티를 올리면 이후
    // findWithDetailsByMemberId의 JOIN FETCH가 캐시 히트로 무시돼 associations이 미초기화된 채로 남는다.
    // member_id 컬럼은 @OneToOne unique=true 제약으로 unique 인덱스가 자동 생성돼 단건 조회에 인덱스를 쓴다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT sp.id FROM StudentProfile sp WHERE sp.member.id = :memberId")
    Optional<Long> lockByMemberId(@Param("memberId") Long memberId);
}
