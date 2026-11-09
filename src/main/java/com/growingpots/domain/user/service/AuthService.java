package com.growingpots.domain.user.service;

import com.growingpots.domain.user.client.KakaoOAuthClient;
import com.growingpots.domain.user.client.KakaoUserInfoResponse;
import com.growingpots.domain.user.dto.request.OAuthLoginRequest;
import com.growingpots.domain.user.entity.Member;
import com.growingpots.domain.user.entity.enums.OauthProvider;
import com.growingpots.domain.user.repository.MemberRepository;
import com.growingpots.domain.user.repository.StudentProfileRepository;
import com.growingpots.global.exception.BaseException;
import com.growingpots.global.response.error.ErrorCode;
import com.growingpots.global.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final MemberRepository memberRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final KakaoOAuthClient kakaoOAuthClient;
    private final JwtTokenProvider jwtTokenProvider;
    private final MemberCreationService memberCreationService;

    public record LoginResult(String accessToken, String refreshToken, boolean onboardingCompleted, String nickname) {}
    public record ReissueResult(String accessToken, String refreshToken) {}

    // login()에는 의도적으로 @Transactional을 걸지 않는다(#294). 하나의 긴 트랜잭션으로 묶으면 카카오 API 호출과
    // insert()(REQUIRES_NEW) 중첩 동안 커넥션을 최대 2개까지 쥐어, 신규 가입이 풀 크기만큼 몰리면 풀이 고갈된다.
    // 조회/생성/토큰저장을 각각 짧은 개별 트랜잭션으로 나눠 한 번에 커넥션 1개만 쓰게 한다. 단계마다 새 트랜잭션
    // (새 스냅샷)이라 기본 격리(RR)에서도 방금 커밋된 회원이 보이므로 격리수준 조정이나 merge가 필요 없다(#283).
    // 트레이드오프: 로그인이 원자적이지 않다 - 회원 생성 후 토큰 저장이 실패하면 토큰만 누락될 수 있으나,
    // insert()가 이미 REQUIRES_NEW라 원래도 비원자적이었고 재로그인으로 복구된다.
    public LoginResult login(OAuthLoginRequest request) {
        OauthProvider provider = parseProvider(request.provider());
        KakaoUserInfoResponse userInfo = kakaoOAuthClient.getUserInfo(request.oauthAccessToken());

        Member member = findOrCreateMember(provider, userInfo);
        // 온보딩 완료 = 분석확인 화면에서 "확인"까지 누른 시점 (기존에는 PDF 분석 완료 시점이었음).
        // PDF 분석까지만 끝나고 분석확인 화면에서 이탈하면(뒤로가기 등) 재로그인 시 그 화면부터 다시
        // 보여줘야 하므로, GraduationAnalysisSummary 존재만으로는 "완료"로 잘못 판단하게 된다.
        boolean onboardingCompleted = studentProfileRepository.existsByMemberAndOnboardingConfirmedAtIsNotNull(member);

        String accessToken = jwtTokenProvider.generateToken(member.getId().toString());
        String refreshToken = jwtTokenProvider.generateRefreshToken(member.getId().toString());
        // refreshToken 저장은 별도의 짧은 트랜잭션에 위임한다. 새 트랜잭션에서 id로 다시 조회하면 managed 상태라
        // dirty checking으로 갱신이 반영되고, login()이 트랜잭션을 쥐고 있지 않아 커넥션도 1개만 쓴다(#283, #294).
        memberCreationService.updateRefreshToken(member.getId(), refreshToken);

        return new LoginResult(accessToken, refreshToken, onboardingCompleted, member.getNickname());
    }

    @Transactional
    public ReissueResult reissue(String refreshToken) {
        JwtTokenProvider.ValidatedToken validated = jwtTokenProvider.validate(refreshToken);
        if (!validated.isValid()) {
            throw new BaseException(validated.errorCode());
        }
        if (!"refresh".equals(validated.type())) {
            throw new BaseException(ErrorCode.INVALID_TOKEN);
        }

        Long memberId = Long.valueOf(validated.subject());
        // 프론트에서 "발급된 지 몇 분 안 된 refreshToken인데 reissue가 401난다"는 제보가 반복됨(#233).
        // JWT 자체는 유효한데 DB에 저장된 현재 토큰과 문자열이 달라서 나는 에러라, "제출된 토큰"과
        // "DB에 실제 저장된 토큰"을 나란히 남겨야 재현 없이도 원인(로테이션 경합 vs 클라이언트가
        // 계속 같은 죽은 토큰을 재전송하는 것)을 구분할 수 있다. 토큰 전체가 아니라 앞 12자만 남긴다.
        Member member = memberRepository.findByIdAndRefreshToken(memberId, refreshToken)
                .orElseThrow(() -> {
                    log.warn("refresh token mismatch: memberId={}, presentedPrefix={}, storedPrefix={}",
                            memberId, tokenPrefix(refreshToken), storedTokenPrefix(memberId));
                    return new BaseException(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
                });

        String subject = memberId.toString();
        String newAccessToken = jwtTokenProvider.generateToken(subject);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(subject);
        member.updateRefreshToken(newRefreshToken);

        return new ReissueResult(newAccessToken, newRefreshToken);
    }

    private String tokenPrefix(String token) {
        return token == null ? "null" : token.substring(0, Math.min(12, token.length()));
    }

    private String storedTokenPrefix(Long memberId) {
        return memberRepository.findById(memberId)
                .map(m -> tokenPrefix(m.getRefreshToken()))
                .orElse("member-not-found");
    }

    private Member findOrCreateMember(OauthProvider provider, KakaoUserInfoResponse userInfo) {
        String oauthId = String.valueOf(userInfo.id());
        return memberRepository.findByOauthProviderAndOauthId(provider, oauthId)
                .orElseGet(() -> {
                    Member newMember = Member.builder()
                            .nickname(extractNickname(userInfo))
                            .oauthProvider(provider)
                            .oauthId(oauthId)
                            .email(userInfo.kakaoAccount() != null ? userInfo.kakaoAccount().email() : null)
                            .build();
                    try {
                        return memberCreationService.insert(newMember);
                    } catch (DataIntegrityViolationException e) {
                        // 동시 요청이 먼저 커밋한 회원을 재조회한다. login()은 트랜잭션 밖이라 이 조회가 새 스냅샷으로 실행돼 그 회원이 보인다(#283).
                        return memberRepository.findByOauthProviderAndOauthId(provider, oauthId)
                                .orElseThrow(() -> new BaseException(ErrorCode.INTERNAL_SERVER_ERROR));
                    }
                });
    }

    private String extractNickname(KakaoUserInfoResponse userInfo) {
        if (userInfo.properties() == null || userInfo.properties().nickname() == null) {
            throw new BaseException(ErrorCode.KAKAO_NICKNAME_UNAVAILABLE);
        }
        return userInfo.properties().nickname();
    }

    private OauthProvider parseProvider(String provider) {
        try {
            return OauthProvider.valueOf(provider.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BaseException(ErrorCode.INVALID_OAUTH_PROVIDER, provider);
        }
    }
}