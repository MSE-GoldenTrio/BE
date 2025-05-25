package com.example.iplan.config.jwt;

import com.example.iplan.auth.UserRole;
import com.example.iplan.auth.Users;
import com.example.iplan.auth.oauth2.CustomOAuth2UserDetails;
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

    // CustomOAuth2UserDetails 정보를 가지고 AccessToken, RefreshToken을 생성하는 메서드
    public JwtToken generateToken(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new RuntimeException("JWT 생성 실패: 인증 정보가 없습니다.");
        }

        CustomOAuth2UserDetails userDetails = (CustomOAuth2UserDetails) authentication.getPrincipal();

        String nickname = userDetails.getUser().getNickname();
        List<String> linked_id = userDetails.getUser().getLinked_id();

        // 사용자 권한 리스트 추출 (ROLE_CHILD, ROLE_PARENT → CHILD, PARENT 변환)
        // authentication.getAuthorities()에서 GrantedAuthority의 getAuthority()를 호출하여 문자열(ROLE_CHILD, ROLE_PARENT)을 가져옴
        String roleString = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("JWT 생성 실패: 사용자 권한이 없습니다."));

        // 이때, 문자열이 아니라 Enum 값(CHILD, PARENT)을 저장하려면 UserRole.fromString()을 사용해 변환
        UserRole role = UserRole.fromString(roleString); // 문자열을 Enum(UserRole)로 변환

        log.info("User nickname: {}, role: {}, linked_id: {}", nickname, role, linked_id);

        long now = (new Date()).getTime();

        // Access Token 생성
        Date accessTokenExpiresIn = new Date(now + ACCESS_TOKEN_EXPIRATION);
        String accessToken = Jwts.builder()
                .setSubject(nickname)
                .claim("role", role.name()) // Enum 값 저장 (CHILD, PARENT)
                .claim("linked_id", linked_id)  // 리스트로 저장
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
        log.info("[JwtTokenProvider getAuthentication]");
        log.info("claim.getSubject is 'Nickname' = {}", claims.getSubject());

        String nickname = claims.getSubject();
        List<String> linked_id = claims.get("linked_id", List.class); // claim 없으면 null 반환

        // role 정보가 없는 경우 예외 처리
        if (claims.get("role") == null) {
            throw new RuntimeException("JWT에 role 정보가 없습니다.");
        }

        // claims.get("role", String.class)에서 가져오는 값이 이미 **Enum 값(CHILD, PARENT)**이므로
        String roleStr = claims.get("role", String.class);
        log.info("User role: {}", roleStr);
        UserRole role = UserRole.valueOf(roleStr);  // 이미 문자열이 Enum 값과 같으므로 UserRole fromString 변환 필요 없음
        log.info("User role Enum changed successfully: {}", role);

        // 권한 설정
        List<GrantedAuthority> authorities = Collections.singletonList(new SimpleGrantedAuthority(role.getRole()));

        // CustomOAuth2UserDetails 생성
        CustomOAuth2UserDetails principal = new CustomOAuth2UserDetails(
                Users.builder()
                        .name("")
                        .email("")
                        .nickname(nickname)  // 토큰에서 추출한..
                        .password("")
                        .authority(role)    // Enum 값 그대로 사용
                        .linked_id(linked_id)
                        .build()
        );

        // Spring Security에서 UsernamePasswordAuthenticationToken을 생성할 때 첫 번째 매개변수는 Principal(사용자 정보) 역할을 함
        // -> principal 대신 nickname을 매개변수로 넣어서 @AuthenticationPrincipal에서 바로 nickname 가져올 수 있도록 함!!

//        String nickname = claims.getSubject();
//        return new UsernamePasswordAuthenticationToken(nickname, "", authorities);
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
