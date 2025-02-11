package com.example.iplan.Domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.google.cloud.firestore.annotation.DocumentId;
import com.google.firebase.database.annotations.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 하루의 데이터
 */
@Component
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "하루 계획의 모든 정보 데이터를 담은 엔티티")
public class DayData {

    @Schema(description = "하루 계획 데이터의 고유 ID", example = "12345", requiredMode = Schema.RequiredMode.REQUIRED)
    @DocumentId
    private String id;

    @Schema(description = "유저 ID", example = "1user2345", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    private String user_id;

    @Schema(description = "해당 날의 년도", example = "2025", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonFormat(pattern = "yyyy")
    @NotNull
    private String year;

    @Schema(description = "해당 날의 월", example = "01", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonFormat(pattern = "MM")
    @NotNull
    private String month;

    @Schema(description = "해당 날", example = "22", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonFormat(pattern = "dd")
    @NotNull
    private String date;

    @Schema(description = "해당 날에 있는 계획 ID 리스트", example = "[\"df-xc123\", \"34nXd@\"]")
    private List<String> plan_idList;

    @Schema(description = "해당 날의 스크린 타임 데이터 Id", example = "screenTime3294-xd")
    private String screenTime_id;

    @Schema(description = "해당 날 보상 지급 여부", example = "false")
    private Boolean is_rewarded;
}
