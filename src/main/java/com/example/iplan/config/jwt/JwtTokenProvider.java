package com.example.iplan.config.jwt;

import com.example.iplan.auth.CustomUserDetails;
import com.example.iplan.auth.UserRole;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.*;

@Slf4j
@Component
public class JwtTokenProvider {
    private final Key key;

    private static final long ACCESS_TOKEN_EXPIRATION = 86400000L; // 24시간
    private static final long REFRESH_TOKEN_EXPIRATION = 604800000L; // 7일

    // application.yml에서 secret 값 가져와서 key에 저장
    public JwtTokenProvider(@Value("${jwt.secret}") String secretKey) {
        log.info("key:"+secretKey);
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    // CustomUserDetails 정보를 가지고 AccessToken, RefreshToken을 생성하는 메서드
    public JwtToken generateToken(Authentication authentication) {
        long now = (new Date()).getTime();

        // 사용자 권한 리스트 추출
        String role = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .orElse("ROLE_CHILD"); // 기본값 (child)

        // Access Token 생성
        Date accessTokenExpiresIn = new Date(now + ACCESS_TOKEN_EXPIRATION);
        String accessToken = Jwts.builder()
                .setSubject(authentication.getName()) // username 저장
                .claim("role", role) // 역할 추가
                .setExpiration(accessTokenExpiresIn)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        // Refresh Token 생성
        Date refreshTokenExpiresIn = new Date(now + REFRESH_TOKEN_EXPIRATION);
        String refreshToken = Jwts.builder()
                .setExpiration(refreshTokenExpiresIn)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        return JwtToken.builder()
                .grantType("Bearer")
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }

    // Jwt 토큰을 복호화(디코딩)하여 토큰에 들어있는 사용자 정보 추출
    public Authentication getAuthentication(String accessToken) {
        // Jwt 토큰 복호화
        Claims claims = parseClaims(accessToken);
        log.info("[JwtTokenProvider getAuthentication] claim.getSubject: {}", claims.getSubject());

        // role 정보가 없는 경우 예외 처리
        if (claims.get("role") == null) {
            throw new RuntimeException("JWT에 role 정보가 없습니다.");
        }

        // 문자열 role을 UserRole Enum으로 변환
        String roleStr = claims.get("role", String.class);
        UserRole role = UserRole.fromString(roleStr);   // Enum 변환

        // 권한 설정
        List<GrantedAuthority> authorities = Collections.singletonList(new SimpleGrantedAuthority(role.getRole()));

        // CustomUserDetails 생성
        CustomUserDetails principal = CustomUserDetails.builder()
                .username(claims.getSubject())
                .password("") // 비밀번호는 필요 없음
                .role(role)
                .build();

        return new UsernamePasswordAuthenticationToken(principal, "", authorities);
    }

    // JWT 유효성 검증
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (SecurityException | MalformedJwtException e) {
            log.error("Invalid JWT Token", e);
        } catch (ExpiredJwtException e) {
            log.error("Expired JWT Token", e);
        } catch (UnsupportedJwtException e) {
            log.error("Unsupported JWT Token", e);
        } catch (IllegalArgumentException e) {
            log.error("JWT claims string is empty.", e);
        }
        return false;
    }


    // 토큰에서 Claims 추출 (만료된 토큰도 처리)
    private Claims parseClaims(String accessToken) { //토큰 파싱, 검증 수행
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(accessToken)
                    .getBody();
        } catch (ExpiredJwtException e) {
            return e.getClaims();
        }
    }

}
