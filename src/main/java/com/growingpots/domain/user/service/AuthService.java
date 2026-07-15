package com.growingpots.domain.user.service;

import com.growingpots.domain.transcript.repository.GraduationAnalysisSummaryRepository;
import com.growingpots.domain.user.client.KakaoOAuthClient;
import com.growingpots.domain.user.client.KakaoUserInfoResponse;
import com.growingpots.domain.user.dto.request.OAuthLoginRequest;
import com.growingpots.domain.user.entity.Member;
import com.growingpots.domain.user.entity.enums.OauthProvider;
import com.growingpots.domain.user.repository.MemberRepository;
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
    private final GraduationAnalysisSummaryRepository graduationAnalysisSummaryRepository;
    private final KakaoOAuthClient kakaoOAuthClient;
    private final JwtTokenProvider jwtTokenProvider;
    private final MemberCreationService memberCreationService;

    public record LoginResult(String accessToken, String refreshToken, boolean onboardingCompleted, String nickname) {}
    public record ReissueResult(String accessToken, String refreshToken) {}

    @Transactional
    public LoginResult login(OAuthLoginRequest request) {
        OauthProvider provider = parseProvider(request.provider());
        KakaoUserInfoResponse userInfo = kakaoOAuthClient.getUserInfo(request.oauthAccessToken());

        Member member = findOrCreateMember(provider, userInfo);
        // 온보딩 완료 = PDF 분석까지 끝난 시점(#221). StudentProfile만 있고 아직 PDF를 안 올렸으면
        // (기본정보입력만 하고 이탈한 경우) 다시 로그인했을 때 온보딩 화면부터 다시 보여줘야 한다 -
        // StudentProfile 존재 여부만으로는 이 상태를 "완료"로 잘못 판단하게 된다.
        boolean onboardingCompleted = graduationAnalysisSummaryRepository.existsByStudentMajor_StudentProfile_Member(member);

        String accessToken = jwtTokenProvider.generateToken(member.getId().toString());
        String refreshToken = jwtTokenProvider.generateRefreshToken(member.getId().toString());
        member.updateRefreshToken(refreshToken);

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