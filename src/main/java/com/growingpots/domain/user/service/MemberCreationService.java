package com.growingpots.domain.user.service;

import com.growingpots.domain.user.entity.Member;
import com.growingpots.domain.user.entity.enums.OauthProvider;
import com.growingpots.domain.user.repository.MemberRepository;
import com.growingpots.global.exception.BaseException;
import com.growingpots.global.response.error.ErrorCode;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberCreationService {

    private final MemberRepository memberRepository;

    // 중복 키 충돌 시 이 트랜잭션만 롤백되고 호출자의 login() 트랜잭션은 오염되지 않는다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Member insert(Member member) {
        return memberRepository.saveAndFlush(member);
    }

    // 동시 요청이 먼저 커밋한 회원을, 호출자(login())의 RR 스냅샷이 아닌 새 트랜잭션(새 스냅샷)으로 재조회한다(#283).
    // 같은 트랜잭션에서 재조회하면 REPEATABLE READ 스냅샷이 고정돼 있어 상대가 커밋한 회원을 보지 못한다.
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<Member> findByOauthInNewTransaction(OauthProvider provider, String oauthId) {
        return memberRepository.findByOauthProviderAndOauthId(provider, oauthId);
    }

    // login()의 member는 insert()(REQUIRES_NEW)에서 와 호출자 기준 detached이고, login()의 RR 스냅샷엔
    // 방금 커밋된 행이 보이지 않아 save()(merge)로 직접 저장하면 실패한다(#283). 새 트랜잭션(새 스냅샷)에서
    // id로 다시 조회하면 커밋된 행이 보이고 managed 상태라, dirty checking으로 refreshToken 갱신이 반영된다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateRefreshTokenInNewTransaction(Long memberId, String refreshToken) {
        memberRepository.findById(memberId)
                .orElseThrow(() -> new BaseException(ErrorCode.INTERNAL_SERVER_ERROR))
                .updateRefreshToken(refreshToken);
    }
}
