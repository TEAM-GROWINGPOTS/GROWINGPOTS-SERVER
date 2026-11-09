package com.growingpots.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.growingpots.domain.user.client.KakaoOAuthClient;
import com.growingpots.domain.user.client.KakaoUserInfoResponse;
import com.growingpots.domain.user.dto.request.OAuthLoginRequest;
import com.growingpots.domain.user.repository.MemberRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class AuthLoginConcurrencyTest {

    private static final int POOL_SIZE = 3;

    // 커넥션 풀을 작게 고정해 "신규 로그인이 커넥션을 2개씩 쥐면 고갈된다"를 검증 가능하게 만든다.
    @DynamicPropertySource
    static void smallConnectionPool(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> String.valueOf(POOL_SIZE));
        registry.add("spring.datasource.hikari.minimum-idle", () -> String.valueOf(POOL_SIZE));
        registry.add("spring.datasource.hikari.connection-timeout", () -> "5000");
    }

    @Autowired
    private AuthService authService;

    @Autowired
    private MemberRepository memberRepository;

    @MockitoBean
    private KakaoOAuthClient kakaoOAuthClient;

    @AfterEach
    void tearDown() {
        memberRepository.deleteAll();
    }

    // #294 회귀: login()이 긴 트랜잭션을 가지면 신규 가입이 커넥션을 2개씩(바깥 tx + insert REQUIRES_NEW) 쥐어,
    // 풀 크기만큼 동시에 몰리면 서로 두 번째 커넥션을 기다리다 풀이 고갈된다. 비트랜잭션 + 단계별 짧은 트랜잭션이면
    // 한 번에 1개만 써서 풀 크기와 같은 수의 동시 신규 로그인도 전부 성공해야 한다. (단일 요청 테스트로는 못 잡는다.)
    @Test
    void 신규_동시_로그인이_풀_크기만큼_몰려도_모두_성공한다() throws Exception {
        AtomicInteger idSeq = new AtomicInteger(700000);
        // 서로 다른 신규 유저(= 각자 insert 경로). 카카오 응답을 살짝 지연시켜 동시에 겹치게 한다.
        when(kakaoOAuthClient.getUserInfo(anyString())).thenAnswer(invocation -> {
            Thread.sleep(200);
            long id = idSeq.getAndIncrement();
            return new KakaoUserInfoResponse(id,
                    new KakaoUserInfoResponse.Properties("동시신규" + id), null);
        });

        List<Throwable> errors = runConcurrentLogins(POOL_SIZE);

        assertThat(errors).as("동시 신규 로그인 중 실패/풀 고갈이 없어야 함").isEmpty();
        assertThat(memberRepository.count()).isEqualTo(POOL_SIZE);
    }

    // #283 회귀: 동일 OAuth ID 요청이 동시에 들어와 둘 다 빈 첫 조회 후 insert가 충돌하면, 진 쪽은
    // DataIntegrityViolationException을 받아 재조회로 복구해야 한다(INTERNAL_SERVER_ERROR 없이). 회원은 1명만 생성.
    // (주의: 이전 스냅샷 재조회로 인한 회귀는 MySQL REPEATABLE READ에서만 재현되며 H2로는 못 잡는다 - 그 부분은
    //  Docker MySQL 8로 수동 검증함. 이 테스트는 중복 복구 경로 자체가 동작하는지를 보장한다.)
    @Test
    void 동일_유저_동시_첫로그인시_중복을_복구하고_모두_성공하며_회원은_하나다() throws Exception {
        int threads = 3;
        CyclicBarrier barrier = new CyclicBarrier(threads);
        // 모든 스레드가 같은 신규 유저(동일 id). 카카오에서 함께 풀려나 findByOauth를 동시에 치도록 배리어로 맞춘다.
        when(kakaoOAuthClient.getUserInfo(anyString())).thenAnswer(invocation -> {
            barrier.await(10, TimeUnit.SECONDS);
            return new KakaoUserInfoResponse(709999L,
                    new KakaoUserInfoResponse.Properties("동시중복"), null);
        });

        List<Throwable> errors = runConcurrentLogins(threads);

        assertThat(errors).as("중복 예외를 복구하지 못해 실패한 요청이 없어야 함").isEmpty();
        assertThat(memberRepository.count()).as("동일 유저이므로 회원은 1명만 생성돼야 함").isEqualTo(1);
    }

    // count개의 로그인을 동시에 실행하고, 각 스레드에서 발생한 예외를 모아 반환한다.
    private List<Throwable> runConcurrentLogins(int count) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(count);
        CountDownLatch start = new CountDownLatch(1);
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            final String token = "token-" + i;
            futures.add(pool.submit(() -> {
                try {
                    start.await();
                    authService.login(new OAuthLoginRequest("KAKAO", token));
                } catch (Throwable t) {
                    errors.add(t);
                }
            }));
        }
        start.countDown();
        for (Future<?> future : futures) {
            future.get(20, TimeUnit.SECONDS);
        }
        pool.shutdown();
        return errors;
    }
}
