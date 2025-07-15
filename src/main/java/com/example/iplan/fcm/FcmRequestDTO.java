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
    private String user_id;
    private String fcmToken;
    private Notification notification;

    @lombok.Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class Notification {
        private String title;  // 알림 제목
        private String body;   // 알림 본문
    }
}
