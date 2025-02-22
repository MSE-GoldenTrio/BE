package com.example.iplan.DTO;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.google.firebase.database.annotations.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.cglib.core.Local;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

@Getter
@Setter
@Builder
@Schema(description = "계획 하나의 데이터를 나타내는 DTO")
public class PlanChildDTO {

    @Schema(description = "계획 데이터 고유 ID", example = "12345")
    private String id;

    @Schema(description = "사용자 Nickname", example = "user123")
    private String user_id;

    @Schema(description = "계획 제목", example = "수학 익힘책 23p 풀기", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    private String title;

    @Schema(description = "알람 설정 여부", example = "true")
    private boolean alarm;

    @Schema(description = "해당 계획의 카테고리", example = "[\"학원\", \"숙제\"]")
    private List<String> category_id;

    @Schema(description = "계획에 대한 부연 설명 혹은 중요한 점 메모", example = "10p 참고하면서 하기")
    private String memo;

    @Schema(description = "계획이 추가된 날짜", example = "2025-01-22", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonFormat(pattern = "yyyy-MM-dd")
    @NotNull
    private String post_date;

    @Schema(description = "계획 달성 여부", example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    private boolean is_completed;
}
