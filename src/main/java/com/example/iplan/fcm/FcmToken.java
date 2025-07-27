package com.example.iplan.fcm;

import com.google.cloud.firestore.annotation.DocumentId;
import lombok.*;
import org.springframework.stereotype.Component;

@Component
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FcmToken {

    @DocumentId
    private String id; // Firestore 문서 ID

    private String user_id; // 유저 닉네임 -> 해시값으로 들어감!

    private String token; // FCM 토큰

    private long createdAt; // 등록 시간 (타임스탬프)
}
