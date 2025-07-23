package com.example.iplan.Domain;

import com.google.cloud.firestore.annotation.DocumentId;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import org.springframework.stereotype.Component;

@Component
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "계정 연동 요청 엔티티")
public class PendingAccountRequest {
    @DocumentId
    private String id;

    private String childHashedNickname;
    private String parentHashedNickname;
    private boolean approved; // 자녀가 승인했는지 여부
    private String status;  // 요청 상태 (pending, approved, denied)
}
