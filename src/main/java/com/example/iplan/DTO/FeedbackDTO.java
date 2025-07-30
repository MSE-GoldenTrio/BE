package com.example.iplan.DTO;

import com.google.cloud.Timestamp;
import com.google.firebase.database.annotations.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeedbackDTO {

    private String reward_id; // 연관된 보상의 ID

    private String child_id; // 아이의 닉네임

    private String comment; // 부모님의 코멘트

    private int grade; // 별점 (1~5 사이의 점수)

    private boolean success; // 보상을 지급하였는지 보류하였는지 (계획을 모두 달성했는가)

    @Schema(description = "문서 최종 수정 시간")
    private Timestamp updated_at;
}
