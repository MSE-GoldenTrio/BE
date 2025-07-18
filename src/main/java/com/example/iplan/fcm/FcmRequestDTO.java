package com.example.iplan.fcm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FcmRequestDTO {

    // 푸시알림을 받을 유저 닉네임(프론트에서 쓰이는게 아니라 토큰이 더 이상 유효하지 않음 → DB 에서 삭제할 때 쓰임)
    private String user_id;

    private String fcmToken;
    private Notification notification;
    private Data data;

    @lombok.Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class Notification {
        private String title;  // 알림 제목
        private String body;   // 알림 본문
    }

    @lombok.Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class Data {
        private String pendingRequestId;    // 계정 연동 요청 시에 데이터 담아서 보내야됨 (계획 알림 시에는 null로)
        private String sender;
        private String type;
    }
}
