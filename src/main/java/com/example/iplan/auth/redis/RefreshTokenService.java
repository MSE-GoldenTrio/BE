package com.example.iplan.auth.redis;

import com.example.iplan.auth.oauth2.CustomOAuth2UserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {
    private final RefreshTokenRepository refreshTokenRepository;

    // 저장 (로그인, 토큰 재발급 시 사용)
    public void saveToken(CustomOAuth2UserDetails userDetails, String refreshToken, long expirationMinutes) {
        RefreshToken token = new RefreshToken(userDetails, refreshToken, expirationMinutes);
        refreshTokenRepository.save(token);
    }

    // 삭제 (로그아웃 시 사용)
    public void deleteToken(String nickname) {
        refreshTokenRepository.deleteById(nickname);
    }

    // 조회 (재발급 시 검증용)
    public Optional<RefreshToken> getToken(String nickname) {
        return refreshTokenRepository.findById(nickname);
    }

    // 검증 (nickname 기준 refreshToken 일치 여부 확인)
    public boolean validateToken(String nickname, String requestRefreshToken) {
        return refreshTokenRepository.findById(nickname)
                .map(saved -> saved.getRefreshToken().equals(requestRefreshToken))
                .orElse(false);
    }
}
