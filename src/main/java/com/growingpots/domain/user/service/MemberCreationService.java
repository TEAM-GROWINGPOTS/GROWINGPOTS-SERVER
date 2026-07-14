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

    // 중복 키 충돌 시 이 트랜잭션만 롤백되고 호출자의 login() 트랜잭션은 오염되지 않는다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Member insert(Member member) {
        return memberRepository.saveAndFlush(member);
    }
}
