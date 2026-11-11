package com.growingpots.domain.user.repository;

import com.growingpots.domain.user.entity.Member;
import com.growingpots.domain.user.entity.enums.OauthProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByOauthProviderAndOauthId(OauthProvider oauthProvider, String oauthId);

    Optional<Member> findByIdAndRefreshToken(Long id, String refreshToken);

    // 로그아웃: 넘겨받은 토큰이 DB에 그대로 저장돼 있을 때만 null로 지운다(compare-and-set).
    // 동시에 들어온 재발급이 토큰을 이미 회전시켰다면 refreshToken 조건이 어긋나 0건이 되어,
    // 방금 갱신된 새 토큰을 덮어쓰지 않는다. 반환값은 실제로 지운 행 수.
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Member m SET m.refreshToken = null WHERE m.id = :id AND m.refreshToken = :refreshToken")
    int clearRefreshToken(@Param("id") Long id, @Param("refreshToken") String refreshToken);
}
