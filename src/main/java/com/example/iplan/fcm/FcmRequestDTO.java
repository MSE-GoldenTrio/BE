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

        private String pendingRequestId;    //계획 알림 시에는 null
        // pendingRequestId의 쓰임
        // 1. 계정 연동 '요청' 시에는 아이가 해당 요청(PendingAccountRequest)의 Id를 받아야 함!!
        // -> 승인/거부 수락 시 AccountRequestDTO 에 id 값을 넣어서 보내야하므로

        // 2. 계정 연동 ''해제' 시에도 부모가 해제하면 아이에게 해당 해제 요청 Id(PendingAccountRequest)를 보냄
        // -> 아이가 이를 확인하면 PendingAccountRequest 에서 id 값에 따라 부모, 아이 닉네임을 기반으로 토큰 재발급 가능(계정 연동 요청 로직과 같음)

        private String sender;
        private String type;
    }
}
