package com.example.iplan.scheduler;

import com.example.iplan.Domain.PlanChild;
import com.example.iplan.Service.AlarmService;
import com.example.iplan.fcm.FcmRequestDTO;
import com.example.iplan.fcm.FcmRequestService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.example.iplan.util.AES256Encryptor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ExecutionException;

@Slf4j
public class PushTask implements Runnable {

    private final PlanChild plan;
    private final String fcmToken;
    private final FcmRequestService fcmRequestService;
    private final AlarmService alarmService;
    private final AES256Encryptor aes;

    // PushTask 클래스의 생성자
    public PushTask(PlanChild plan, String fcmToken, FcmRequestService fcmRequestService, AlarmService alarmService, AES256Encryptor aes) {
        this.plan = plan;
        this.fcmToken = fcmToken;
        this.fcmRequestService = fcmRequestService;
        this.alarmService = alarmService;
        this.aes = aes;
    }

    @Override
    public void run() {
        String decryptedTitle;
        try {
            decryptedTitle = aes.decrypt(plan.getTitle()); // 복호화
        } catch (Exception e) {
            log.error("제목 복호화 실패: {}", e.getMessage());
            decryptedTitle = "(제목 오류)";
        }

        // 1. FcmRequestDTO 생성
        FcmRequestDTO requestDTO = FcmRequestDTO.builder()
                .user_id(plan.getHashed_user_id())
                .fcmToken(fcmToken)
                .notification(FcmRequestDTO.Notification.builder()
                        .title("iPlan")
                        .body(decryptedTitle + " 시작할 시간이에요!")
                        .build())
                .data(FcmRequestDTO.Data.builder()
                        .pendingRequestId(null)
                        .sender(null)
                        .type("PlanAlarm")
                        .build())
                .build();

        // 2. 푸시알림 전송
        try {
            fcmRequestService.sendPush(requestDTO);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        // 3. 알림 컬렉션에서 제거
        // 예외 처리 필수 -> PushTask.run() 메서드는 Runnable 인터페이스이므로 예외를 throw 할 수 없음
        try {
            alarmService.deleteOneAlarm(plan.getId(), fcmToken);
        } catch (ExecutionException | InterruptedException e) {
            log.error("푸시알림 전송 후 알림을 컬렉션에서 제거하는 도중 오류 발생 Error: {}", e.getMessage());
        }
    }
}
