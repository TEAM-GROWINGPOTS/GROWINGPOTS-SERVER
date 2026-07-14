package com.growingpots.domain.user.service;

import com.growingpots.domain.user.entity.Member;
import com.growingpots.domain.user.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberCreationService {

    private final MemberRepository memberRepository;

    // REQUIRES_NEW: 외부 login() 트랜잭션과 완전히 분리된 독립 트랜잭션으로 실행.
    // 중복 키 충돌 시 이 트랜잭션만 롤백되며 외부 트랜잭션은 rollback-only로 오염되지 않는다.
    // saveAndFlush: 즉시 flush해 예외를 이 메서드 경계 안에서 발생시킨다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Member insert(Member member) {
        return memberRepository.saveAndFlush(member);
    }
}
