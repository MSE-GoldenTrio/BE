package com.example.iplan.auth;

import com.example.iplan.auth.DTO.TokenRefreshRequestDTO;
import com.example.iplan.auth.jwt.JwtProperties;
import com.example.iplan.auth.jwt.JwtToken;
import com.example.iplan.auth.jwt.JwtTokenProvider;
import com.example.iplan.auth.oauth2.CustomOAuth2UserDetails;
import com.example.iplan.auth.redis.RefreshTokenService;
import com.example.iplan.auth.redis.RefreshToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api")
public class AuthController {

    private final JwtProperties jwtProperties;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final UserService userService;

    @DeleteMapping("/delete-account")
    public ResponseEntity<String> deleteAccount(@RequestHeader("Authorization") String authHeader,
                                           @RequestHeader("fcm-token") String fcmToken,
                                           @AuthenticationPrincipal CustomOAuth2UserDetails customOAuth2UserDetails) throws Exception {
        String accessToken = authHeader.replace("Bearer ", "");
        String encryptedUserId = customOAuth2UserDetails.getUsername();
        String result = userService.withdraw(accessToken, fcmToken, encryptedUserId); // 탈퇴 처리 서비스 호출
        return ResponseEntity.ok(result);
    }

    @PostMapping("/auth/refresh")
    public ResponseEntity<?> refreshAccessToken(@RequestBody TokenRefreshRequestDTO request) throws Exception {
        log.info("토큰 재발급 요청 옴!!");

        String accessToken = request.getAccessToken();
        String refreshToken = request.getRefreshToken();
        String fcmToken = request.getFcmToken();
        log.info("토큰 재발급 요청 fcmToken: {}", fcmToken);
        log.info("토큰 재발급 요청 accessToken: {}", accessToken);
        log.info("토큰 재발급 요청 refreshToken: {}", refreshToken);

        // 1. accessToken 에서 암호화 되어있는!! nickname 추출
        String encryptedNickname;
        try {
            encryptedNickname = jwtTokenProvider.getEncryptedId(accessToken);
            log.info("암호화된 유저 닉네임: {}", encryptedNickname);
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "유효하지 않은 AccessToken 입니다."));
        }

        // 2. Redis 에서 refreshToken 조회 및 검증
        // -> 유저 닉네임(암호화)과 기기 fcmToken 으로 조회(여러 기기에 로그인 가능하므로)
        Optional<RefreshToken> optional = refreshTokenService.getToken(encryptedNickname, fcmToken);
      
        if (optional.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "RefreshToken이 만료되었거나 존재하지 않습니다."));
        }

        RefreshToken savedToken = optional.get();

        if (!savedToken.getRefreshToken().equals(refreshToken)) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "RefreshToken이 일치하지 않습니다. 다시 로그인 해주세요."));
        }

        // 3. DB 에서 유저 정보 다시 조회 (최신 linked_id 등을 위해) 후 CustomOAuth2UserDetails 재생성
        CustomOAuth2UserDetails userDetails = userService.loadUserByEncryptedNickname(encryptedNickname);

        if (userDetails == null) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "사용자 정보를 찾을 수 없습니다."));
        }

        // 4. 새 Authentication 객체 생성
        Authentication authentication = jwtTokenProvider.createAuthentication(userDetails);

        // 5. 만료까지 남은 시간 확인
        Date expirationDate = jwtTokenProvider.getExpirationDate(refreshToken);
        long now = System.currentTimeMillis();
        long remainingMillis = expirationDate.getTime() - now;
        long oneWeekMillis = 7L * 24 * 60 * 60 * 1000; // 7일
//         테스트용: 1분으로 설정
//        long oneWeekMillis = 60 * 1000;


        // 6. refreshToken 은 조건에 따라 새로 발급하거나 유지
        JwtToken newToken;
        if (remainingMillis <= oneWeekMillis) {
            log.info("RefreshToken 남은 기간이 7일 이내 → 갱신 수행");
            newToken = jwtTokenProvider.generateToken(authentication);

            long expirationMinutes = jwtProperties.getRefreshTokenExpiration() / 1000 / 60;
            refreshTokenService.saveToken((CustomOAuth2UserDetails) authentication.getPrincipal(), fcmToken, newToken.getRefreshToken(), expirationMinutes);
        } else {

            // 7. accessToken 은 항상 새로 발급
            log.info("RefreshToken 충분히 남아 있음 → accessToken만 재발급");

            String newAccessToken = jwtTokenProvider.generateNewAccessToken(authentication);

            newToken = JwtToken.builder()
                    .grantType("Bearer")
                    .accessToken(newAccessToken)
                    .refreshToken(refreshToken) // 기존 refresh 유지
                    .build();
        }

        return ResponseEntity.ok(newToken);
    }

}
