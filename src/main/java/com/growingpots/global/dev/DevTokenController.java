package com.growingpots.global.dev;

import com.growingpots.domain.user.entity.Member;
import com.growingpots.domain.user.repository.MemberRepository;
import com.growingpots.global.exception.BaseException;
import com.growingpots.global.response.BaseResponse;
import com.growingpots.global.response.error.ErrorCode;
import com.growingpots.global.response.success.SuccessCode;
import com.growingpots.global.security.JwtTokenProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 로컬 개발 환경에서 Postman 등으로 인증이 필요한 API를 테스트하기 위한 임시 토큰 발급용. local 프로파일에서만 활성화된다.
// 기존 회원의 refreshToken도 실제 로그인처럼 갱신해 저장하므로, 발급받은 refreshToken을 그대로 /reissue 테스트에 쓸 수 있다.
@Tag(name = "[DEV ONLY] Dev Token", description = "개발/테스트 전용 API — 배포 전 제거 예정")
@Profile("local")
@RestController
@RequiredArgsConstructor
public class DevTokenController {

    private final MemberRepository memberRepository;
    private final JwtTokenProvider jwtTokenProvider;

    @Operation(
            summary = "[개발용] 임시 토큰 발급",
            description = "실제 로그인 없이 지정한 memberId로 accessToken/refreshToken을 발급한다. "
                    + "local 프로파일에서만 동작하며, 개발 중 Postman/Swagger 테스트 용도로만 사용한다. 프로덕션 배포 대상 아님."
    )
    @GetMapping("/api/v1/dev/token")
    @Transactional
    public BaseResponse<DevTokenResponse> issueToken(@RequestParam Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));

        String accessToken = jwtTokenProvider.generateToken(memberId.toString());
        String refreshToken = jwtTokenProvider.generateRefreshToken(memberId.toString());
        member.updateRefreshToken(refreshToken);

        return BaseResponse.success(SuccessCode.OK, new DevTokenResponse(accessToken, refreshToken));
    }

    private record DevTokenResponse(String accessToken, String refreshToken) {
    }
}
