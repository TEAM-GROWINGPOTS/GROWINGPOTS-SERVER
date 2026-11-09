package com.growingpots.domain.user.service;

import com.growingpots.domain.user.entity.Member;
import com.growingpots.domain.user.repository.MemberRepository;
import com.growingpots.global.exception.BaseException;
import com.growingpots.global.response.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberCreationService {

    private final MemberRepository memberRepository;

    // 중복 키 충돌 시 이 트랜잭션만 롤백된다. login()은 트랜잭션 밖이라, 충돌 예외를 받은 호출부가 재조회로 복구한다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Member insert(Member member) {
        return memberRepository.saveAndFlush(member);
    }

    // login()에서 토큰 저장을 위임받는다. login()이 트랜잭션을 쥐지 않으므로 이 짧은 트랜잭션이 커넥션을 1개만
    // 쓰고(#294), 새 트랜잭션이라 방금 커밋된 회원을 id로 조회해 managed 상태로 갱신한다(기본 격리에서도 보임, #283).
    @Transactional
    public void updateRefreshToken(Long memberId, String refreshToken) {
        memberRepository.findById(memberId)
                .orElseThrow(() -> new BaseException(ErrorCode.INTERNAL_SERVER_ERROR))
                .updateRefreshToken(refreshToken);
    }
}
