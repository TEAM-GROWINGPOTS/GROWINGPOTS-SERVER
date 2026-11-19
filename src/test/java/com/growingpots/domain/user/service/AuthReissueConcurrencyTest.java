package com.growingpots.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;

import com.growingpots.domain.user.client.KakaoOAuthClient;
import com.growingpots.domain.user.entity.Member;
import com.growingpots.domain.user.entity.enums.OauthProvider;
import com.growingpots.domain.user.repository.MemberRepository;
import com.growingpots.global.exception.BaseException;
import com.growingpots.global.response.error.ErrorCode;
import com.growingpots.global.security.JwtTokenProvider;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

// #302: 로그아웃과 재발급이 거의 동시에 들어올 때, 재발급이 "조건 없이" 새 토큰을 저장하면
// 방금 로그아웃으로 무효화한 토큰 자리를 되살려 세션이 부활할 수 있다. 재발급 저장을 compare-and-set으로
// 바꿔 "조회한 옛 토큰이 그대로일 때만" 교체되고, 경합에 지면 401이 되는지 검증한다.
// H2 기본 격리수준이 READ_COMMITTED라 "조회 이후 커밋된 로그아웃"이 재발급 UPDATE에 보여 경합이 재현된다.
@SpringBootTest
@ActiveProfiles("test")
class AuthReissueConcurrencyTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    // 재발급의 "토큰 조회"와 "토큰 교체" 사이에 로그아웃을 끼워넣기 위해 findByIdAndRefreshToken을 스파이로 막는다.
    @MockitoSpyBean
    private MemberRepository memberRepository;

    // 컨텍스트가 요구하는 외부 클라이언트. 이 테스트에선 호출하지 않는다.
    @MockitoBean
    private KakaoOAuthClient kakaoOAuthClient;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
        memberRepository.deleteAll();
    }

    @Test
    void 정상_재발급은_DB_refreshToken을_새_값으로_회전시킨다() {
        Member member = saveMemberWithRefreshToken("3001");
        String oldToken = member.getRefreshToken();

        authService.reissue(oldToken);

        String stored = memberRepository.findById(member.getId()).orElseThrow().getRefreshToken();
        assertThat(stored).isNotNull().isNotEqualTo(oldToken);
    }

    // 이미 회전된(죽은) 옛 토큰으로 재발급하면 조회 단계에서 걸러져 401. (compare-and-set 이전 단계 가드)
    @Test
    void 이미_회전된_옛_토큰으로_재발급하면_401_AUTH_005를_반환한다() {
        Member member = saveMemberWithRefreshToken("3002");
        String oldToken = member.getRefreshToken();
        authService.reissue(oldToken); // oldToken -> newToken 으로 회전

        BaseException error = catchReissue(oldToken);

        assertThat(error.getErrorType()).isEqualTo(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
    }

    // 완료기준 2번: 재발급이 먼저 토큰을 X→Y로 회전시킨 뒤, 옛 토큰 X로 로그아웃하면
    // 로그아웃도 compare-and-set이라 0건이 되어, 재발급된 새 토큰 Y를 null로 지우지 않는다.
    @Test
    void 재발급으로_토큰_회전_후_옛_토큰으로_로그아웃하면_새_토큰이_지워지지_않는다() {
        Member member = saveMemberWithRefreshToken("3004");
        Long memberId = member.getId();
        String oldToken = member.getRefreshToken();

        authService.reissue(oldToken); // X -> Y 회전
        String rotated = memberRepository.findById(memberId).orElseThrow().getRefreshToken();

        authService.logout(oldToken); // 옛 토큰 X로 로그아웃 (멱등, DB에는 영향 없어야 함)

        String stored = memberRepository.findById(memberId).orElseThrow().getRefreshToken();
        assertThat(stored).as("옛 토큰 로그아웃이 재발급된 새 토큰을 지우면 안 됨")
                .isNotNull().isEqualTo(rotated);
    }

    // 핵심 경합: 재발급이 토큰을 조회해 통과한 뒤, 저장하기 전에 로그아웃이 먼저 커밋되면
    // 재발급은 조건부 UPDATE가 0건이 되어 401이고, 로그아웃이 지운 null을 되살리지 않아야 한다.
    @Test
    void 재발급이_토큰_조회후_로그아웃이_먼저_커밋되면_401이고_DB토큰은_null로_유지된다() throws Exception {
        Member member = saveMemberWithRefreshToken("3003");
        Long memberId = member.getId();
        String oldToken = member.getRefreshToken();

        CountDownLatch selected = new CountDownLatch(1); // 재발급이 토큰 조회를 끝낸 시점
        CountDownLatch logoutCommitted = new CountDownLatch(1); // 로그아웃이 커밋을 끝낸 시점
        // 조회가 "통과"(존재/일치)한 척하고 멈춘다. reissue는 반환된 엔티티를 쓰지 않고 존재 여부만 보므로
        // Optional.of(member)면 충분하다. 멈춘 사이 로그아웃이 토큰을 지우도록 양보한다.
        doAnswer(invocation -> {
            selected.countDown();
            logoutCommitted.await(10, TimeUnit.SECONDS);
            return Optional.of(member);
        }).when(memberRepository).findByIdAndRefreshToken(memberId, oldToken);

        Future<BaseException> reissue = executor.submit(() -> catchReissue(oldToken));

        assertThat(selected.await(10, TimeUnit.SECONDS)).isTrue();
        authService.logout(oldToken); // DB의 oldToken -> null 로 커밋
        logoutCommitted.countDown();

        BaseException error = reissue.get(10, TimeUnit.SECONDS);
        assertThat(error).as("경합에 진 재발급은 401이어야 함").isNotNull();
        assertThat(error.getErrorType()).isEqualTo(ErrorCode.REFRESH_TOKEN_NOT_FOUND);

        String stored = memberRepository.findById(memberId).orElseThrow().getRefreshToken();
        assertThat(stored).as("로그아웃이 지운 토큰이 재발급으로 되살아나면 안 됨").isNull();
    }

    private Member saveMemberWithRefreshToken(String oauthId) {
        Member member = memberRepository.save(Member.builder()
                .nickname("재발급동시성")
                .oauthProvider(OauthProvider.KAKAO)
                .oauthId(oauthId)
                .email(null)
                .build());
        String refreshToken = jwtTokenProvider.generateRefreshToken(member.getId().toString());
        member.updateRefreshToken(refreshToken);
        return memberRepository.save(member);
    }

    // 재발급을 호출하고 BaseException을 그대로 돌려준다. 성공해버리면 테스트가 드러나게 실패시킨다.
    private BaseException catchReissue(String refreshToken) {
        try {
            authService.reissue(refreshToken);
        } catch (BaseException e) {
            return e;
        }
        throw new AssertionError("재발급이 실패해야 하는데 성공했다");
    }
}
