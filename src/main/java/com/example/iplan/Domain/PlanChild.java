package com.example.iplan.Domain;

import com.fasterxml.jackson.annotation.JsonFormat;
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

    //User 테이블의 아이디
    @NotNull
    @Schema(description = "사용자 UID", example = "user123")
    private String user_id;

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
    private String post_date;

    @Schema(description = "계획에 대한 부연 설명 혹은 중요한 점 메모", example = "10p 참고하면서 하기")
    private String memo;

    @Schema(description = "계획 달성 여부", example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
    private boolean is_completed;

}