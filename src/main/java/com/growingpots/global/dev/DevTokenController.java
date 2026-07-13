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
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 로컬/dev 개발 환경에서 Postman·Swagger 등으로 인증이 필요한 API를 테스트하기 위한 임시 토큰 발급용.
// 기존 회원의 refreshToken도 실제 로그인처럼 갱신해 저장하므로, 발급받은 refreshToken을 그대로 /reissue 테스트에 쓸 수 있다.
// 인증 없이 memberId만으로 아무 계정의 토큰이나 발급되는 엔드포인트라 prod에는 절대 열면 안 된다.
@Tag(name = "[DEV ONLY] Dev Token", description = "개발/테스트 전용 API — 배포 전 제거 예정")
@Profile({"local", "dev"})
@RestController
@RequiredArgsConstructor
public class DevTokenController {

    private final MemberRepository memberRepository;
    private final JwtTokenProvider jwtTokenProvider;

    @Value("${cookie.secure:true}")
    private boolean cookieSecure;

    @Value("${cookie.same-site:None}")
    private String cookieSameSite;

    @Operation(
            summary = "[개발용] 임시 토큰 발급",
            description = "실제 로그인 없이 지정한 memberId로 accessToken/refreshToken을 발급한다. "
                    + "인증 없이 누구나 호출 가능하고 memberId만 알면 그 계정의 토큰이 발급되니, "
                    + "프론트 Swagger 테스트 목적으로만 짧게 쓰고 끝나는 대로 비활성화해야 한다."
    )
    @GetMapping("/api/v1/dev/token")
    @Transactional
    public BaseResponse<DevTokenResponse> issueToken(@RequestParam Long memberId, HttpServletResponse response) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));

        String accessToken = jwtTokenProvider.generateToken(memberId.toString());
        String refreshToken = jwtTokenProvider.generateRefreshToken(memberId.toString());
        member.updateRefreshToken(refreshToken);

        // AuthController와 동일하게 accessToken도 쿠키로 내려줘야 실제 인증에 쓸 수 있다
        // (JwtAuthenticationFilter가 이제 accessToken 쿠키만 보고, Authorization 헤더는 안 봄).
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from("accessToken", accessToken)
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/api")
                .maxAge(jwtTokenProvider.getExpirationSeconds())
                .sameSite(cookieSameSite)
                .build()
                .toString());
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from("refreshToken", refreshToken)
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/api/v1/auth/reissue")
                .maxAge(jwtTokenProvider.getRefreshExpirationSeconds())
                .sameSite(cookieSameSite)
                .build()
                .toString());

        return BaseResponse.success(SuccessCode.OK, new DevTokenResponse(accessToken, refreshToken));
    }

    private record DevTokenResponse(String accessToken, String refreshToken) {
    }
}
