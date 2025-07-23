package com.example.iplan.DTO;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
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
@JsonInclude(JsonInclude.Include.NON_NULL)  // 불필요한 null 값을 제거할 수 있도록 수정 -> 프론트엔드에서 불필요한 데이터 관리 필요 없음
@Schema(description = "계획 하나의 데이터를 나타내는 DTO")
public class PlanChildDTO {

    @Schema(description = "계획 데이터 고유 ID", example = "12345")
    private String id;

    @Schema(description = "사용자 Nickname", example = "user123")
    private String user_id;

    private String hashed_user_id;

    @Schema(description = "계획 제목", example = "수학 익힘책 23p 풀기", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @Schema(description = "알람 설정 여부", example = "true")
    private boolean alarm;

    @Schema(description = "해당 계획의 카테고리", example = "[\"학원\", \"숙제\"]")
    private List<String> category_id;

    @Schema(description = "계획에 대한 부연 설명 혹은 중요한 점 메모", example = "10p 참고하면서 하기")
    private String memo;

    @JsonFormat(pattern = "yyyy")
    private String post_year;

    @JsonFormat(pattern = "MM")
    private String post_month;

    @JsonFormat(pattern = "dd")
    private String post_day;

    @JsonFormat(pattern = "HH:mm")
    @Schema(description = "계획 시작 시간", example = "14:00")
    private String plan_start_time;

    @JsonFormat(pattern = "HH:mm")
    @Schema(description = "계획 마감 시간", example = "17:25")
    private String plan_end_time;

    @Schema(description = "계획 달성 여부", example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
    private boolean is_completed;
}
