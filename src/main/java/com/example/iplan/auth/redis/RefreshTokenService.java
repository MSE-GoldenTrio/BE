package com.example.iplan.auth.redis;

import com.example.iplan.auth.oauth2.CustomOAuth2UserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {
    private final RefreshTokenRepository refreshTokenRepository;

    // 저장 (로그인, 토큰 재발급 시 사용)
    public void saveToken(CustomOAuth2UserDetails userDetails,
                          String fcmToken,
                          String refreshToken,
                          long expirationMinutes) {
        RefreshToken token = new RefreshToken(userDetails, fcmToken, refreshToken, expirationMinutes);
        log.info("Redis에 RefreshToken 저장: {}", token);
        refreshTokenRepository.save(token);
    }

    // 삭제 (닉네임 + fcmToken 기준)
    public void deleteToken(String nickname, String fcmToken) {
        String key = RefreshToken.compositeKey(nickname, fcmToken);
        log.info("Redis에서 RefreshToken 삭제 key: {}", key);
        refreshTokenRepository.deleteById(key);
    }

    // 조회 (닉네임 + fcmToken 기준)
    public Optional<RefreshToken> getToken(String nickname, String fcmToken) {
        String key = RefreshToken.compositeKey(nickname, fcmToken);
        return refreshTokenRepository.findById(key);
    }

    // 검증 (닉네임 + fcmToken 기준 refreshToken 일치 여부 확인)
    public boolean validateToken(String nickname, String fcmToken, String requestRefreshToken) {
        return getToken(nickname, fcmToken)
                .map(saved -> saved.getRefreshToken().equals(requestRefreshToken))
                .orElse(false);
    }

    /**
     * 소셜로그인 시 임시 fcmToken(placeholder)로 저장한 엔트리를
     * 실제 fcmToken 키로 "재바인딩" (키 마이그레이션)
     */
    public void rebindToFcmWithSessionId(String nickname, String sessionId, String newFcmToken) {
        String oldFcm = "PENDING-" + sessionId;
        String oldKey = RefreshToken.compositeKey(nickname, oldFcm);

        refreshTokenRepository.findById(oldKey).ifPresent(old -> {
            // 같은 nickname 으로 동일 fcmToken 엔트리가 이미 있다면 정리(선택)
            String newKey = RefreshToken.compositeKey(nickname, newFcmToken);
            refreshTokenRepository.deleteById(newKey);

            RefreshToken migrated = new RefreshToken();
            migrated.setNickname(old.getNickname());
            migrated.setFcmToken(newFcmToken);
            migrated.setRefreshToken(old.getRefreshToken());
            migrated.setExpiration(old.getExpiration()); // 남은 TTL(분)
            migrated.setId(newKey);

            refreshTokenRepository.save(migrated);
            refreshTokenRepository.deleteById(oldKey);
        });
    }

}
