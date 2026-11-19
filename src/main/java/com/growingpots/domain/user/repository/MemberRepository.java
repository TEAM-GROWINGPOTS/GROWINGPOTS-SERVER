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

    // 재발급: 제시된(옛) 토큰이 DB에 그대로일 때만 새 토큰으로 교체한다(compare-and-set).
    // 조회와 교체 사이에 로그아웃/다른 재발급이 토큰을 먼저 바꿨다면 refreshToken 조건이 어긋나 0건이 되어,
    // 무효화됐거나 이미 회전된 세션을 새 토큰으로 되살리지 않는다. 반환값은 실제로 바꾼 행 수(#302).
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Member m SET m.refreshToken = :newRefreshToken WHERE m.id = :id AND m.refreshToken = :currentRefreshToken")
    int updateRefreshTokenIfMatches(@Param("id") Long id,
                                    @Param("currentRefreshToken") String currentRefreshToken,
                                    @Param("newRefreshToken") String newRefreshToken);
}
