package com.example.iplan.DTO;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.cloud.Timestamp;
import com.google.firebase.database.annotations.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)  // 불필요한 null 값을 제거할 수 있도록 수정 -> 프론트엔드에서 불필요한 데이터 관리 필요 없음
public class RewardChildDTO {

    @Schema(description = "계획 데이터 고유 ID", example = "12345")
    private String id; // Firestore 문서의 ID

    @Schema(description = "프론트 낙관적 업데이트와 온스냅샷 감지가 동시에 이루어짐에 따라 필요한 매칭 id", example = "temp-2198722398")
    private String temp_id;

    @Schema(description = "아이의 고유 Nickname", example = "user123")
    private String user_id; // 아이의 고유 닉네임

    @Schema(description = "보상의 내용", example = "user123")
    private String content; // 보상의 내용

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private String post_date; // 보상이 적용된 날짜

    @JsonFormat(pattern = "yyyy")
    private String post_year;

    @JsonFormat(pattern = "MM")
    private String post_month;

    @JsonFormat(pattern = "dd")
    private String post_day;

    private boolean rewarded; // 보상이 지급되었는지 여부

    private boolean success; // 보상이 지급 or 보류

    @Schema(description = "문서 최종 수정 시간")
    private Timestamp updated_at;
}
