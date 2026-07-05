package com.growingpots.domain.user.service;

import com.growingpots.domain.user.client.KakaoOAuthClient;
import com.growingpots.domain.user.client.KakaoUserInfoResponse;
import com.growingpots.domain.user.dto.request.OAuthLoginRequest;
import com.growingpots.domain.user.dto.request.TokenReissueRequest;
import com.growingpots.domain.user.dto.response.OAuthLoginResponse;
import com.growingpots.domain.user.dto.response.TokenReissueResponse;
import com.growingpots.domain.user.entity.Member;
import com.growingpots.domain.user.entity.enums.OauthProvider;
import com.growingpots.domain.user.repository.MemberRepository;
import com.growingpots.domain.user.repository.StudentProfileRepository;
import com.growingpots.global.exception.BaseException;
import com.growingpots.global.response.error.ErrorCode;
import com.growingpots.global.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final MemberRepository memberRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final KakaoOAuthClient kakaoOAuthClient;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public OAuthLoginResponse login(OAuthLoginRequest request) {
        OauthProvider provider = parseProvider(request.provider());
        KakaoUserInfoResponse userInfo = kakaoOAuthClient.getUserInfo(request.oauthAccessToken());

        Member member = findOrCreateMember(provider, userInfo);
        boolean onboardingCompleted = studentProfileRepository.existsByMember(member);

        String accessToken = jwtTokenProvider.generateToken(member.getId().toString());
        String refreshToken = jwtTokenProvider.generateRefreshToken(member.getId().toString());
        member.updateRefreshToken(refreshToken);

        return new OAuthLoginResponse(accessToken, refreshToken, onboardingCompleted, member.getNickname());
    }

    @Transactional
    public TokenReissueResponse reissue(TokenReissueRequest request) {
        String refreshToken = request.refreshToken();
        JwtTokenProvider.ValidatedToken validated = jwtTokenProvider.validate(refreshToken);
        if (!validated.isValid()) {
            throw new BaseException(validated.errorCode());
        }

        Long memberId = Long.valueOf(validated.subject());
        Member member = memberRepository.findByIdAndRefreshToken(memberId, refreshToken)
                .orElseThrow(() -> new BaseException(ErrorCode.REFRESH_TOKEN_NOT_FOUND));

        String subject = memberId.toString();
        String newAccessToken = jwtTokenProvider.generateToken(subject);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(subject);
        member.updateRefreshToken(newRefreshToken);

        return new TokenReissueResponse(newAccessToken, newRefreshToken);
    }

    private Member findOrCreateMember(OauthProvider provider, KakaoUserInfoResponse userInfo) {
        String oauthId = String.valueOf(userInfo.id());
        return memberRepository.findByOauthProviderAndOauthId(provider, oauthId)
                .orElseGet(() -> memberRepository.save(Member.builder()
                        .nickname(userInfo.properties().nickname())
                        .oauthProvider(provider)
                        .oauthId(oauthId)
                        .email(userInfo.kakaoAccount() != null ? userInfo.kakaoAccount().email() : null)
                        .build()));
    }

    private OauthProvider parseProvider(String provider) {
        try {
            return OauthProvider.valueOf(provider.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BaseException(ErrorCode.INVALID_OAUTH_PROVIDER, provider);
        }
    }
}
