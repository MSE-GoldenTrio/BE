package com.example.iplan.DTO;

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

    private int grade; // 별점 (1~3 사이의 점수)

    private boolean success; // 보상을 지급하였는지 보류하였는지 (계획을 모두 달성했는가)
}
