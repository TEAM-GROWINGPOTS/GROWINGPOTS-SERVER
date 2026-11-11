package com.growingpots.global.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

// accessToken/refreshToken 쿠키 생성·삭제를 한 곳에서 관리한다.
// AuthController·DevTokenController가 각자 쿠키를 만들면 path/옵션이 어긋나기 쉬워 여기로 모았다.
@Component
public class AuthCookieProvider {

    private static final String ACCESS_TOKEN = "accessToken";
    private static final String REFRESH_TOKEN = "refreshToken";

    // access 쿠키는 모든 API 요청에 실려야 하므로 /api, refresh 쿠키는 auth 엔드포인트(login·reissue·logout)에만 노출한다.
    private static final String ACCESS_PATH = "/api";
    private static final String REFRESH_PATH = "/api/v1/auth";
    // refresh 쿠키 path를 /api/v1/auth/reissue → /api/v1/auth로 넓히기 전에 저장된 옛 path 쿠키.
    // path가 다르면 브라우저는 별개 쿠키로 취급해 새 쿠키가 옛 쿠키를 덮지 않으므로, 함께 삭제해 중복을 막는다.
    // 모든 기존 브라우저가 한 번 재접속하면 불필요해지지만, 남겨둬도 "없는 쿠키 삭제"라 무해하다.
    private static final String LEGACY_REFRESH_PATH = "/api/v1/auth/reissue";

    private final JwtTokenProvider jwtTokenProvider;
    private final boolean secure;
    private final String sameSite;

    public AuthCookieProvider(JwtTokenProvider jwtTokenProvider,
                              @Value("${cookie.secure:true}") boolean secure,
                              @Value("${cookie.same-site:None}") String sameSite) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.secure = secure;
        this.sameSite = sameSite;
    }

    // 로그인·재발급·dev 토큰 발급 공통: 새 토큰 쿠키를 내리면서 옛 path refresh 쿠키도 함께 정리한다.
    public void setAuthCookies(HttpServletResponse response, String accessToken, String refreshToken) {
        addCookie(response, build(ACCESS_TOKEN, accessToken, ACCESS_PATH, jwtTokenProvider.getExpirationSeconds()));
        addCookie(response, build(REFRESH_TOKEN, refreshToken, REFRESH_PATH, jwtTokenProvider.getRefreshExpirationSeconds()));
        addCookie(response, expire(REFRESH_TOKEN, LEGACY_REFRESH_PATH));
    }

    // 로그아웃: access·refresh 쿠키를 모두 삭제한다(옛 path refresh 쿠키 포함).
    public void clearAuthCookies(HttpServletResponse response) {
        addCookie(response, expire(ACCESS_TOKEN, ACCESS_PATH));
        addCookie(response, expire(REFRESH_TOKEN, REFRESH_PATH));
        addCookie(response, expire(REFRESH_TOKEN, LEGACY_REFRESH_PATH));
    }

    private ResponseCookie build(String name, String value, String path, long maxAgeSeconds) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .path(path)
                .maxAge(maxAgeSeconds)
                .sameSite(sameSite)
                .build();
    }

    // maxAge=0 + 빈 값으로 같은 이름·같은 path 쿠키를 즉시 삭제한다.
    private ResponseCookie expire(String name, String path) {
        return build(name, "", path, 0);
    }

    private void addCookie(HttpServletResponse response, ResponseCookie cookie) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
