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
    public ResponseEntity<?> deleteAccount(@RequestHeader("Authorization") String authHeader,
                                           @RequestHeader("fcm-token") String fcmToken,
                                           @AuthenticationPrincipal CustomOAuth2UserDetails customOAuth2UserDetails) throws ExecutionException, InterruptedException {
        String accessToken = authHeader.replace("Bearer ", "");
        String userId = customOAuth2UserDetails.getUsername();
        userService.withdraw(accessToken, fcmToken, userId); // 탈퇴 처리 서비스 호출
        return ResponseEntity.ok(Map.of("message", "회원 탈퇴가 완료되었습니다."));
    }

    @PostMapping("/auth/refresh")
    public ResponseEntity<?> refreshAccessToken(@RequestBody TokenRefreshRequestDTO request) {
        String accessToken = request.getAccessToken();
        String refreshToken = request.getRefreshToken();

        // 1. accessToken 에서 nickname 추출
        String nickname;
        try {
            nickname = jwtTokenProvider.getUserNickname(accessToken);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("유효하지 않은 AccessToken입니다.");
        }

        // 2. Redis에서 refreshToken 조회 및 검증
        Optional<RefreshToken> optional = refreshTokenService.getToken(nickname);
      
        if (optional.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("RefreshToken이 만료되었거나 존재하지 않습니다. 다시 로그인 해주세요.");
        }

        RefreshToken savedToken = optional.get();

        if (!savedToken.getRefreshToken().equals(refreshToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("RefreshToken이 일치하지 않습니다. 다시 로그인 해주세요.");
        }

        // 3. Authentication 추출
        Authentication authentication = jwtTokenProvider.getAuthentication(accessToken);

        // 4. 만료까지 남은 시간 확인
        Date expirationDate = jwtTokenProvider.getExpirationDate(refreshToken);
        long now = System.currentTimeMillis();
        long remainingMillis = expirationDate.getTime() - now;
        long oneWeekMillis = 7L * 24 * 60 * 60 * 1000; // 7일
//         테스트용: 1분으로 설정
//        long oneWeekMillis = 60 * 1000;

        // 5. accessToken 은 항상 새로 발급
        // 6. refreshToken 은 조건에 따라 새로 발급하거나 유지
        JwtToken newToken;
        if (remainingMillis <= oneWeekMillis) {
            log.info("RefreshToken 남은 기간이 7일 이내 → 갱신 수행");
            newToken = jwtTokenProvider.generateToken(authentication);

            long expirationMinutes = jwtProperties.getRefreshTokenExpiration() / 1000 / 60;
            refreshTokenService.saveToken((CustomOAuth2UserDetails) authentication.getPrincipal(), newToken.getRefreshToken(), expirationMinutes);
        } else {
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
