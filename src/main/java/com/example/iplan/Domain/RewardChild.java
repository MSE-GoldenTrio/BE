package com.example.iplan.Domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.annotation.DocumentId;
import com.google.cloud.firestore.annotation.PropertyName;
import com.google.firebase.database.annotations.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RewardChild {

    @DocumentId
    private String id; // Firestore 문서의 ID (보상의 id)

    // 서버에 계획이 저장되는 순간 온스냅샷이 실행되는데
    // 만약 온스냅샷이 먼저 실행된다면 프론트에서 낙관전 업데이트를 위해 발급한 tempId가 치환이 되지 않을 수 있음
    // -> tempId를 서버에도 같이 보내어 저장 후, 온스냅샷 감지 이후 단일 계획 반환 시에 tempId도 같이 반환하여 계획 필터링 하도록 함
    @Schema(description = "프론트 낙관적 업데이트와 온스냅샷 감지가 동시에 이루어짐에 따라 필요한 매칭 id", example = "temp-2198722398")
    private String temp_id;

    @NotNull
    private String user_id; // 아이의 고유 ID

    @NotNull
    private String content; // 보상의 내용

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private String post_date; // 보상이 적용된 날짜

    @JsonFormat(pattern = "yyyy")
    private String post_year;

    @JsonFormat(pattern = "MM")
    private String post_month;

    @JsonFormat(pattern = "dd")
    private String post_day;

    private boolean rewarded; // 보상이 지급되었는지 여부 -> 첨삭 여부

    private boolean success; // 보상이 지급 or 보류

    @Schema(description = "문서 최종 수정 시간")
    private Timestamp updated_at;

    /*
    private boolean is_rewarded; // 보상이 지급되었는지 여부

    // Firestore에서 "_rewarded"로 저장되지 않도록 getter와 setter에 @PropertyName 추가
    @PropertyName("is_rewarded")
    public boolean is_rewarded() {
        return is_rewarded;
    }

    @PropertyName("is_rewarded")
    public void setRewarded(boolean rewarded) {
        is_rewarded = rewarded;
    }

     */

}
