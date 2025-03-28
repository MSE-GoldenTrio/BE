package com.example.iplan.DTO;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)  // 불필요한 null 값을 제거할 수 있도록 수정 -> 프론트엔드에서 불필요한 데이터 관리 필요 없음
public class RewardChildDTO {

    private String id; // Firestore 문서의 ID

    private String user_id; // 아이의 고유 닉네임

    private String content; // 보상의 내용

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private String date; // 보상이 적용된 날짜

    @JsonFormat(pattern = "yyyy")
    private String year;

    @JsonFormat(pattern = "MM")
    private String month;

    @JsonFormat(pattern = "dd")
    private String day;

    private String plan_id; // 보상을 지급할 plan

    private boolean rewarded; // 보상이 지급되었는지 여부

    private boolean success; // 보상이 지급 or 보류
}
