package com.example.DunbarHorizon.global.security;

import com.example.DunbarHorizon.global.security.exception.ExpiredTokenException;
import com.example.DunbarHorizon.global.security.exception.InvalidTokenException;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private final Key key;
    private final long accessTokenValidityInMilliseconds;
    private final long refreshTokenValidityInMilliseconds;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-expiration-seconds}") long accessExpirationSeconds,
            @Value("${jwt.refresh-expiration-seconds}") long refreshExpirationSeconds) {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.accessTokenValidityInMilliseconds = accessExpirationSeconds * 1000;
        this.refreshTokenValidityInMilliseconds = refreshExpirationSeconds * 1000;
    }

    public String createAccessToken(AuthPrincipal principal) {
        return createToken(principal, accessTokenValidityInMilliseconds);
    }

    public String createRefreshToken(AuthPrincipal principal) {
        return createToken(principal, refreshTokenValidityInMilliseconds);
    }

    private String createToken(AuthPrincipal principal, long validity) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + validity);

        return Jwts.builder()
                .setSubject(principal.id().toString())
                .claim("role", principal.role())
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(key, SignatureAlgorithm.HS512)
                .compact();
    }

    /**
     * jjwt 예외를 도메인 예외로 변환하는 유일한 지점이다.
     *
     * <p>필터 경로({@code JwtAuthenticationFilter} → {@code JwtAuthenticationEntryPoint})와
     * 재발급 경로({@code LoginService} → {@code GlobalExceptionHandler})는 서로 도달할 수 없는
     * 별개의 출구지만, 여기서 변환한 덕분에 양쪽 모두 동일한 예외 타입을 보게 된다.
     * 그 결과 두 출구가 같은 {@code error} / {@code message}를 응답하게 되어,
     * 하드코딩된 문자열 없이 응답 일관성이 보장된다.
     *
     * <p>{@code io.jsonwebtoken} 타입이 이 클래스 밖으로 새어나가지 않도록 유지할 것.
     * 서명 실패는 {@code io.jsonwebtoken.security.SignatureException}이며 {@link JwtException}
     * 하위이므로, 하위 타입을 나열하지 않고 {@link JwtException}으로 일괄 처리한다.
     */
    public AuthPrincipal validateToken(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            Long userId = Long.parseLong(claims.getSubject());
            String role = claims.get("role", String.class);

            return new AuthPrincipal(userId, role);

        } catch (ExpiredJwtException ex) {
            throw new ExpiredTokenException();
        } catch (JwtException | IllegalArgumentException ex) {
            throw new InvalidTokenException();
        }
    }

    public LocalDateTime getExpirationTime(String token) {
        Date expiration = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getExpiration();

        return expiration.toInstant()
                .atZone(ZoneOffset.UTC)
                .toLocalDateTime();
    }
}
