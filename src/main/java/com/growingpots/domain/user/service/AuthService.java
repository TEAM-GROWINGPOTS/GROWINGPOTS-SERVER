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
import org.springframework.transaction.annotation.Isolation;
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

    // 신규 회원은 insert()(REQUIRES_NEW)가 login()의 첫 조회 '이후'에 커밋하는데, 기본 격리(RR)에선 login()이
    // 첫 조회 시점 스냅샷을 고정해 그 회원을 못 본다(#283) - 재조회·refreshToken 저장(merge)이 모두 실패한다.
    // READ_COMMITTED는 조회마다 최신 커밋을 보므로 같은 트랜잭션에서 해결된다(격리수준 조정 선례: #268 PlannerService).
    @Transactional(isolation = Isolation.READ_COMMITTED)
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
        // 신규 가입 경로의 member는 insert()(REQUIRES_NEW)에서 와 detached지만, READ_COMMITTED라 save()의 merge
        // 조회가 방금 커밋된 행을 보므로 갱신이 반영된다. 기존 회원은 managed라 save()가 사실상 no-op이다(#283).
        member.updateRefreshToken(refreshToken);
        member = memberRepository.save(member);

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
                        // 동시 요청이 먼저 커밋한 회원을 재조회한다. READ_COMMITTED라 같은 트랜잭션에서도 커밋된 행이 보인다(#283).
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