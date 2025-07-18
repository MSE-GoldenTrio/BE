package com.example.iplan.fcm;

import com.example.iplan.ExceptionHandler.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Slf4j
@Service
@RequiredArgsConstructor
public class FcmTokenService {

    private final FcmTokenRepository fcmTokenRepository;

    /**
     * 사용자 ID와 FCM 토큰을 저장 (중복 토큰은 저장하지 않음)
     */
    public void save(String nickname, String token) {
        try {
            // 1. 이미 존재하는지 확인 (userId + token 조합)
            FcmToken existingToken = fcmTokenRepository.findByUserIdAndToken(nickname, token);

            if (existingToken != null) {
                log.info("이미 등록된 FCM 토큰: nickname={}, token={}", nickname, token);
                return;
            }

            // 2. 새 FCM 토큰 저장
            FcmToken newToken = FcmToken.builder()
                    .user_id(nickname)
                    .token(token)
                    .createdAt(System.currentTimeMillis())
                    .build();

            fcmTokenRepository.saveWithAutoIncrement(newToken);
            log.info("FCM 토큰 저장 완료: userId={}, token={}", nickname, token);

        } catch (ExecutionException | InterruptedException e) {
            log.error("FCM 토큰 저장 실패: {}", e.getMessage());
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt(); // interrupt 상태 복구
            }
            throw new RuntimeException("FCM 토큰 저장 중 오류 발생", e);
        }
    }

    /**
     * 특정 사용자 ID의 모든 FCM 토큰 조회
     */
    public List<FcmToken> getTokensByUserId(String userId) {
        try {
            return fcmTokenRepository.findByUserId(userId);
        } catch (ExecutionException | InterruptedException e) {
            log.error("FCM 토큰 조회 실패", e);
            Thread.currentThread().interrupt();
            return List.of();
        }
    }

    /**
     * 로그아웃 등으로 인해 FCM 토큰 제거
     */
    public void deleteToken(String userId, String token) {
        try {
            // 제거할 토큰 검색
            FcmToken tokenToDelete = fcmTokenRepository.findByUserIdAndToken(userId, token);
            if (tokenToDelete != null) {
                fcmTokenRepository.delete(tokenToDelete);
                log.info("FCM 토큰 삭제 완료: userId={}, token={}", userId, token);
            } else {
                throw new CustomException("삭제할 FCM 토큰이 존재하지 않음: token = " + token, HttpStatus.NOT_FOUND);
            }
        } catch (ExecutionException | InterruptedException e) {
            log.error("FCM 토큰 삭제 실패", e);
            Thread.currentThread().interrupt();
            throw new CustomException("FCM 토큰 삭제 실패", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

}

