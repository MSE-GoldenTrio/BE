package com.example.iplan.Domain;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.annotation.DocumentId;
import com.google.firebase.database.annotations.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import org.springframework.stereotype.Component;

@Component
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Feedback {

    @DocumentId
    private String id; // Firestore 문서의 ID (지급한 보상의 id)

    @NotNull
    private String user_id; // 부모님의 닉네임

    @NotNull
    private String child_id;  // 아이의 닉네임

    @NotNull
    private String reward_id;   // 아이들이 작성한 보상과 맵핑

    private String comment; // 부모님의 코멘트

    private int grade; // 부모님의 별점

    private boolean rewarded; // 보상이 지급되었는지 여부 -> 첨삭 여부

    private boolean success; // 보상이 지급 or 보류

    @Schema(description = "문서 최종 수정 시간")
    @NotNull
    private Timestamp updated_at;
}
