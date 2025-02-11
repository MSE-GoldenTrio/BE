package com.example.iplan.DTO;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.google.firebase.database.annotations.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@Schema(description = "하루 계획의 모든 정보 데이터를 담은 DTO")
public class DayDataDTO {

    @Schema(description = "하루 계획 데이터의 고유 ID", example = "12345")
    private String id;

    @NotNull
    @Schema(description = "유저 ID", example = "1user2345", requiredMode = Schema.RequiredMode.REQUIRED)
    private String user_id;

    @JsonFormat(pattern = "yyyy-MM-dd")
    @Schema(description = "해당 날의 년/월/일", example = "2025-02-22", requiredMode = Schema.RequiredMode.REQUIRED)
    private String date;

    @JsonFormat(pattern = "dd")
    @Schema(description = "해당 날", example = "22", requiredMode = Schema.RequiredMode.REQUIRED)
    private String day;

    @Schema(description = "해당 날에 있는 계획 ID 리스트", example = "[\"df-xc123\", \"34nXd@\"]")
    private List<PlanChildDTO> planChildDTOList;

    @Schema(description = "해당 날의 스크린 타임 목표 달성 여부", example = "false")
    private boolean screenTime_goal;

    @Schema(description = "해당 날 보상 내용", example = "차나핑 가방 사주기")
    private String reward_content;

    @Schema(description = "해당 날 보상 지급 여부", example = "false")
    private boolean is_reward;
}
