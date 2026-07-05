package com.growingpots.global.security;

import com.growingpots.global.response.error.ErrorCode;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;

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
        this.decoder = NimbusJwtDecoder.withSecretKey(key).build();
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
        } catch (JwtValidationException e) {
            log.debug("JWT validation failed: {}", e.getMessage());
            boolean isExpired = e.getErrors().stream()
                    .anyMatch(err -> err.getDescription() != null
                            && err.getDescription().contains("Jwt expired"));
            return Optional.of(isExpired ? ErrorCode.EXPIRED_TOKEN : ErrorCode.INVALID_TOKEN);
        } catch (Exception e) {
            log.warn("Invalid JWT: {}", e.getMessage());
            return Optional.of(ErrorCode.INVALID_TOKEN);
        }
    }
}