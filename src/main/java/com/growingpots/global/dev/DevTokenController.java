package com.growingpots.global.dev;

import com.growingpots.global.response.BaseResponse;
import com.growingpots.global.response.success.SuccessCode;
import com.growingpots.global.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 로컬 개발 환경에서 Postman 등으로 인증이 필요한 API를 테스트하기 위한 임시 토큰 발급용. local 프로파일에서만 활성화된다.
@Profile("local")
@RestController
@RequiredArgsConstructor
public class DevTokenController {

    private final JwtTokenProvider jwtTokenProvider;

    @GetMapping("/api/v1/dev/token")
    public BaseResponse<String> issueToken(@RequestParam Long memberId) {
        return BaseResponse.success(SuccessCode.OK, jwtTokenProvider.generateToken(memberId.toString()));
    }
}
