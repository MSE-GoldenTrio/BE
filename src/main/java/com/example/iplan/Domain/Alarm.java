package com.example.iplan.Domain;

import com.google.cloud.firestore.annotation.DocumentId;
import lombok.*;
import org.springframework.stereotype.Component;

@Component
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Alarm {

    @DocumentId
    private String id;

    private String plan_id; // 연결된 계획 ID

    private String fcmToken;    //  알림을 보낼 fcm token

}
