package com.growingpots.domain.user.repository;

import com.growingpots.domain.user.entity.Member;
import com.growingpots.domain.user.entity.enums.OauthProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByOauthProviderAndOauthId(OauthProvider oauthProvider, String oauthId);

    Optional<Member> findByIdAndRefreshToken(Long id, String refreshToken);
}
