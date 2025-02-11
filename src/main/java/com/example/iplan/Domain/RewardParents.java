package com.example.iplan.Domain;

import com.google.cloud.firestore.annotation.DocumentId;
import com.google.cloud.firestore.annotation.PropertyName;
import com.google.firebase.database.annotations.NotNull;
import lombok.*;
import org.springframework.stereotype.Component;

@Component
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RewardParents {

    @DocumentId
    private String id; // Firestore 문서의 ID (지급한 보상의 id)

    @NotNull
    private String plan_id; // 아이의 계획 ID와 매칭

    @NotNull
    private String user_id; // 부모님의 사용자 ID

    @NotNull
    private String reward_id;   // 아이들이 작성한 보상과 맵핑

    private String comment; // 부모님의 코멘트

    private int grade; // 부모님의 별점

    private boolean rewarded; // 보상이 지급되었는지 여부

    private boolean success; // 보상을 지급했는지 보류했는지 (계획을 모두 완료했는가)

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
