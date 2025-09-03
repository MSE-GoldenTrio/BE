package com.example.iplan.Domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.annotation.DocumentId;
import com.google.firebase.database.annotations.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "계획 하나의 데이터를 나타내는 엔티티")
public class PlanChild {

    @DocumentId
    @Schema(description = "계획 데이터 고유 ID", example = "12345")
    private String id; // Firestore 문서의 ID


    // 서버에 계획이 저장되는 순간 온스냅샷이 실행되는데
    // 만약 온스냅샷이 먼저 실행된다면 프론트에서 낙관전 업데이트를 위해 발급한 tempId가 치환이 되지 않을 수 있음
    // -> tempId를 서버에도 같이 보내어 저장 후, 온스냅샷 감지 이후 단일 계획 반환 시에 tempId도 같이 반환하여 계획 필터링 하도록 함
    @Schema(description = "프론트 낙관적 업데이트와 온스냅샷 감지가 동시에 이루어짐에 따라 필요한 매칭 id", example = "temp-2198722398")
    private String temp_id;

    @NotNull
    @Schema(description = "사용자 닉네임", example = "user123")
    private String user_id;

    @NotNull
    private String hashed_user_id;

    @NotNull
    @Schema(description = "계획 제목", example = "수학 익힘책 23p 풀기", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @Schema(description = "알람 설정 여부", example = "true")
    private boolean alarm;

    //카테고리 테이블의 아이디
    @Schema(description = "해당 계획의 카테고리", example = "[\"학원\", \"숙제\"]")
    private List<String> category_id;

    @JsonFormat(pattern = "yyyy")
    @Schema(description = "계획이 추가된 날짜의 년", example = "2025", requiredMode = Schema.RequiredMode.REQUIRED)
    private String post_year;

    @JsonFormat(pattern = "MM")
    @Schema(description = "계획이 추가된 날짜의 월", example = "01", requiredMode = Schema.RequiredMode.REQUIRED)
    private String post_month;

    @JsonFormat(pattern = "dd")
    @Schema(description = "계획이 추가된 날짜의 일", example = "22", requiredMode = Schema.RequiredMode.REQUIRED)
    private String post_day;

    @JsonFormat(pattern = "HH:mm")
    @Schema(description = "계획 시작 시간", example = "14:00")
    private String plan_start_time;

    @JsonFormat(pattern = "HH:mm")
    @Schema(description = "계획 마감 시간", example = "17:25")
    private String plan_end_time;

    @Schema(description = "계획에 대한 부연 설명 혹은 중요한 점 메모", example = "10p 참고하면서 하기")
    private String memo;

    @Schema(description = "계획 달성 여부", example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonProperty("is_completed")
    private boolean is_completed;

    @Schema(description = "문서 최종 수정 시간")
    private Timestamp updated_at;

}