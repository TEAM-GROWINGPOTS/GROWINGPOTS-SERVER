package com.growingpots.global.security;

import com.growingpots.global.response.error.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.WebUtils;

import java.io.IOException;
import java.util.Collections;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);

        if (token != null) {
            JwtTokenProvider.ValidatedToken validated = jwtTokenProvider.validate(token);
            if (!validated.isValid()) {
                request.setAttribute("exception", validated.errorCode());
            } else if (!"access".equals(validated.type())) {
                request.setAttribute("exception", ErrorCode.INVALID_TOKEN);
            } else {
                // TODO: User 엔티티 구현 후 UserDetailsService로 교체
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(validated.subject(), null, Collections.emptyList());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        filterChain.doFilter(request, response);
    }

    // 웹이 accessToken을 Bearer 헤더로 보내는 구조로 전환 중이라 헤더를 먼저 본다.
    // 전환 전 웹은 accessToken 쿠키를 쓰므로 웹 배포 전까지는 쿠키도 받는다(#286에서 제거 예정).
    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            if (StringUtils.hasText(token)) {
                return token;
            }
        }
        Cookie cookie = WebUtils.getCookie(request, "accessToken");
        return cookie != null ? cookie.getValue() : null;
    }

}