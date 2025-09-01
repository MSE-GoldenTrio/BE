package com.example.iplan.fcm;

import com.example.iplan.auth.UserRepository;
import com.example.iplan.auth.Users;
import com.example.iplan.auth.oauth2.CustomOAuth2UserDetails;
import com.example.iplan.auth.redis.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api")
public class FcmTokenController {

    private final FcmTokenService fcmTokenService;
    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;

    /**
     * 소셜로그인 이후 발급받은 FcmToken 저장
     */
    @PostMapping("/register-fcm-token")
    public ResponseEntity<?> registerFcmToken(@RequestBody Map<String, String> request, @AuthenticationPrincipal CustomOAuth2UserDetails customOAuth2UserDetails) {
        String fcmToken = request.get("fcmToken");
        String sessionId = request.get("sessionId");    // FE가 딥링크로 전달받아 함께 보냄

        Users user = userRepository.findByEncryptedNickname(customOAuth2UserDetails.getUsername()).orElseThrow(() -> new IllegalArgumentException("User not found."));

        fcmTokenService.save(user.getNicknameHash(), fcmToken); // 또는 업데이트

        // 1) 유저별 fcmToken 저장/갱신
        fcmTokenService.save(user.getNicknameHash(), fcmToken);

        // 2) placeholder → 실제 fcmToken 으로 키 마이그레이션
        refreshTokenService.rebindToFcmWithSessionId(customOAuth2UserDetails.getUsername(), sessionId, fcmToken);

        return ResponseEntity.ok().build();
    }

}
