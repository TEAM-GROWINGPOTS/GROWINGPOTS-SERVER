package com.growingpots.global.security;

import com.growingpots.global.response.error.ErrorCode;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
public class JwtTokenProvider {

    private final long expiration;
    private final long refreshExpiration;
    private final JwtEncoder encoder;
    private final JwtDecoder decoder;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration:1800000}") long expiration,
            @Value("${jwt.refresh-expiration:1209600000}") long refreshExpiration) {
        this.expiration = expiration;
        this.refreshExpiration = refreshExpiration;
        SecretKeySpec key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        OctetSequenceKey jwk = new OctetSequenceKey.Builder(key).build();
        this.encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
        NimbusJwtDecoder nimbusJwtDecoder = NimbusJwtDecoder.withSecretKey(key).build();
        // 기본 JwtTimestampValidator는 60초 clock skew를 허용하는데, 발급/검증이 같은 서버라 보정할 시간차가 없음
        nimbusJwtDecoder.setJwtValidator(new JwtTimestampValidator(Duration.ZERO));
        this.decoder = nimbusJwtDecoder;
    }

    public String generateToken(String subject) {
        return generateToken(subject, expiration);
    }

    public String generateRefreshToken(String subject) {
        return generateToken(subject, refreshExpiration);
    }

    private String generateToken(String subject, long tokenExpiration) {
        Instant now = Instant.now();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plusMillis(tokenExpiration))
                .claim("jti", UUID.randomUUID().toString())
                .build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public String getSubject(String token) {
        return decoder.decode(token).getSubject();
    }

    public Optional<ErrorCode> extractErrorCode(String token) {
        try {
            decoder.decode(token);
            return Optional.empty();
        } catch (Exception e) {
            return Optional.of(toErrorCode(e));
        }
    }

    // extractErrorCode + getSubject를 각각 호출하면 토큰을 두 번 디코딩하게 되어, 한 번의 디코딩으로 묶어 제공한다.
    public ValidatedToken validate(String token) {
        try {
            Jwt jwt = decoder.decode(token);
            return new ValidatedToken(null, jwt.getSubject());
        } catch (Exception e) {
            return new ValidatedToken(toErrorCode(e), null);
        }
    }

    private ErrorCode toErrorCode(Exception e) {
        if (e instanceof JwtValidationException validationException) {
            log.debug("JWT validation failed: {}", e.getMessage());
            boolean isExpired = validationException.getErrors().stream()
                    .anyMatch(err -> err.getDescription() != null
                            && err.getDescription().contains("Jwt expired"));
            return isExpired ? ErrorCode.EXPIRED_TOKEN : ErrorCode.INVALID_TOKEN;
        }
        log.warn("Invalid JWT: {}", e.getMessage());
        return ErrorCode.INVALID_TOKEN;
    }

    public record ValidatedToken(ErrorCode errorCode, String subject) {
        public boolean isValid() {
            return errorCode == null;
        }
    }
}