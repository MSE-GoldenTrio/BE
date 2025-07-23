package com.example.iplan.auth.jwt;

import com.example.iplan.auth.ExceptionHandler.CustomAuthenticationException;
import com.example.iplan.auth.UserRole;
import com.example.iplan.auth.Users;
import com.example.iplan.auth.oauth2.CustomOAuth2UserDetails;
import com.example.iplan.auth.redis.Blacklist;
import com.example.iplan.auth.redis.BlacklistRepository;
import com.example.iplan.auth.redis.RefreshTokenService;
import com.example.iplan.util.AES256Encryptor;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
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
    private final RefreshTokenService refreshTokenService;
    private final BlacklistRepository blacklistRepository;

    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;

    private final AES256Encryptor aes;

//    public static final long ACCESS_TOKEN_EXPIRATION = 1000L * 60 * 60 * 24; // 24시간
//    public static final long REFRESH_TOKEN_EXPIRATION = 1000L * 60 * 60 * 24 * 30; // 30일

    // JwtProperties 를 통해 생성자 내부에서 key 를 초기화
    public JwtTokenProvider(
            RefreshTokenService refreshTokenService,
            BlacklistRepository blacklistRepository,
            JwtProperties jwtProperties,
            AES256Encryptor aes
    ) {
        this.refreshTokenService = refreshTokenService;
        this.blacklistRepository = blacklistRepository;

        this.accessTokenExpiration = jwtProperties.getAccessTokenExpiration();
        this.refreshTokenExpiration = jwtProperties.getRefreshTokenExpiration();

        byte[] keyBytes = Decoders.BASE64.decode(jwtProperties.getSecret());
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.aes = aes;
    }

    // CustomOAuth2UserDetails 정보를 가지고 AccessToken, RefreshToken을 생성하는 메서드
    public JwtToken generateToken(Authentication authentication) throws Exception {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new RuntimeException("JWT 생성 실패: 인증 정보가 없습니다.");
        }

        CustomOAuth2UserDetails userDetails = (CustomOAuth2UserDetails) authentication.getPrincipal();
        String nickname = userDetails.getUsername();
        String email = userDetails.getEmail();
        List<String> linked_id = userDetails.getUser().getLinked_id();

        // 사용자 권한 리스트 추출 (ROLE_CHILD, ROLE_PARENT → CHILD, PARENT 변환)
        // authentication.getAuthorities()에서 GrantedAuthority의 getAuthority()를 호출하여 문자열(ROLE_CHILD, ROLE_PARENT)을 가져옴
        String roleString = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("JWT 생성 실패: 사용자 권한이 없습니다."));

        // 이때, 문자열이 아니라 Enum 값(CHILD, PARENT)을 저장하려면 UserRole.fromString()을 사용해 변환
        UserRole role = UserRole.fromString(roleString); // 문자열을 Enum(UserRole)로 변환

        log.info("User nickname: {}, email: {}, role: {}, linked_id: {}", nickname, email, role, linked_id);

        long now = (new Date()).getTime();
        List<String> decodedLinkedId = new ArrayList<>();
        for(String id : linked_id){
            decodedLinkedId.add(aes.decrypt(id));
        }

        // Access Token 생성
        Date accessTokenExpiresIn = new Date(now + accessTokenExpiration);
        String accessToken = Jwts.builder()
                .setSubject(aes.decrypt(nickname))
                .claim("email", aes.decrypt(email))
                .claim("role", role.name()) // Enum 값 저장 (CHILD, PARENT)
                .claim("linked_id", decodedLinkedId)  // 리스트로 저장
                .setExpiration(accessTokenExpiresIn)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        // Refresh Token 생성
        Date refreshTokenExpiresIn = new Date(now + refreshTokenExpiration);
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
    public Authentication getAuthentication(String accessToken) throws Exception {
        // Jwt 토큰 복호화
        Claims claims = parseClaims(accessToken);
        log.info("[JwtTokenProvider getAuthentication]");
        log.info("claim.getSubject is 'Nickname' = {}", claims.getSubject());

        String nickname = claims.getSubject();
        String email = (String) claims.get("email");
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
                        .email(aes.encrypt(email))
                        .nickname(aes.encrypt(nickname))  // 토큰에서 추출한..
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

    // AccessToken 유효성 검증 - 블랙리스트에 있는지
    public void verifyAccessToken(String token) throws CustomAuthenticationException {
        // 1. 유효성 검사
        if (!validateToken(token)) {
            throw new CustomAuthenticationException("유효하지 않은 Access Token입니다.", HttpStatus.UNAUTHORIZED);
        }

        // 2. 블랙리스트 조회
        Optional<Blacklist> blacklisted = blacklistRepository.findById(token);
        log.info("블랙리스트 조회 완료");

        if (blacklisted.isPresent()) {
            log.info("블랙리스트에 사용자 존재!!");
            throw new CustomAuthenticationException("이미 로그아웃된 사용자입니다.", HttpStatus.UNAUTHORIZED);
        }
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

    // 토큰의 nickname(subject)을 가져옴
    public String getUserNickname(String token) {
        return parseClaims(token).getSubject();
    }

    // 토큰의 만료시간 가져옴
    public Date getExpirationDate(String token) {
        return parseClaims(token).getExpiration();
    }

    // AccessToken 재발급
    public String generateNewAccessToken(Authentication authentication) throws Exception {
        CustomOAuth2UserDetails userDetails = (CustomOAuth2UserDetails) authentication.getPrincipal();

        String nickname = userDetails.getUser().getNickname();
        String email = userDetails.getUser().getEmail();
        List<String> linked_id = userDetails.getUser().getLinked_id();
        UserRole role = userDetails.getUser().getAuthority();

        long now = System.currentTimeMillis();
        Date accessTokenExpiresIn = new Date(now + accessTokenExpiration);

        return Jwts.builder()
                .setSubject(aes.decrypt(nickname))
                .claim("email", aes.decrypt(email))
                .claim("role", role.name())
                .claim("linked_id", linked_id)
                .setExpiration(accessTokenExpiresIn)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public void destroyToken(String accessToken, String reason) {
        // 1. nickname 추출
        String nickname = getUserNickname(accessToken);

        // 2. Redis에서 RefreshToken 삭제
        refreshTokenService.deleteToken(nickname);

        // 3. accessToken 만료 시간 계산 → 현재 시간과의 차이로 TTL 계산
        Date expirationDate = getExpirationDate(accessToken);
        long now = System.currentTimeMillis();
        long remainingMillis = expirationDate.getTime() - now;
        long remainingMinutes = Math.max(1, remainingMillis / 1000 / 60); // 최소 1분

        // 4. 블랙리스트 등록
        Blacklist blacklist = new Blacklist(accessToken, reason, remainingMinutes);
        blacklistRepository.save(blacklist);
        log.info("{} 사용자 토큰 블랙리스트 등록 완료: {}", nickname, accessToken);
    }
}
