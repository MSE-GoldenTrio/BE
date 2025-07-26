package com.example.iplan.fcm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.messaging.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class FcmRequestService {

    private final ObjectMapper objectMapper;
    private final FcmTokenService fcmTokenService;

    /**
     * FCM DTO 기반 푸시 알림 전송
     * @param requestDTO 푸시 알림에 필요한 FCM 토큰 및 메시지 내용
     */
    public void sendPush(FcmRequestDTO requestDTO) throws JsonProcessingException {
        // FCM 메시지 data 구성 -> json 변환
        String dataJson = objectMapper.writeValueAsString(requestDTO.getData());
        log.info("Before: {}", requestDTO.getData());
        log.info("FcmRequest Body: {}", dataJson);

        // FcmRequestDTO 에서 Data 추출 (null 처리)
        FcmRequestDTO.Data data = requestDTO.getData();
        String pendingRequestId = data.getPendingRequestId() == null ? "null" : data.getPendingRequestId(); // null 처리
        String sender = data.getSender() == null ? "null" : data.getSender();
        String type = data.getType() == null ? "null" : data.getType();

        // DTO 기반 Notification 생성
        Notification notification = Notification.builder()
                .setTitle(requestDTO.getNotification().getTitle())
                .setBody(requestDTO.getNotification().getBody())
                .build();

        // 메시지 빌드
        Message message = Message.builder()
                .setToken(requestDTO.getFcmToken())
                .setAndroidConfig(AndroidConfig.builder()
                        .setPriority(AndroidConfig.Priority.HIGH) // 우선 순위를 HIGH로 설정
                        .build())
                .putData("title", requestDTO.getNotification().getTitle())
                .putData("body", requestDTO.getNotification().getBody())
                .putData("pendingRequestId", pendingRequestId)
                .putData("sender", sender)
                .putData("type", type)
                .build();

        try {
            String response = FirebaseMessaging.getInstance().send(message);
            log.info("FCM 전송 성공: {}",response);

        } catch (FirebaseMessagingException e) {
            if (e.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED) {
                log.info("FCM Token 유효하지 않음");

                // 토큰이 더 이상 유효하지 않음 → DB 에서 삭제
                fcmTokenService.deleteToken(requestDTO.getUser_id(), requestDTO.getFcmToken());
            }
        }
    }
}
