package com.example.iplan.Domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.google.cloud.firestore.annotation.DocumentId;
import com.google.firebase.database.annotations.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;

@Component
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "하루의 스크린타임 목표 데이터를 담은 엔티티")
public class ScreenTime {

    @Schema(description = "스크린타임 데이터 고유 Id", example = "sdfi2-zx4", requiredMode = Schema.RequiredMode.REQUIRED)
    @DocumentId
    private String id;

    @Schema(description = "사용자 ID", example = "user123", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    private String user_id;

    @Schema(description = "스크린타임 목표 설정된 날짜", example = "2025-01-22", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonFormat(pattern = "yyyy-MM-dd")
    @NotNull
    private String date;

    // 사용자가 정한 최소 측정 마감 시간
    @Schema(description = "사용자가 해당 시간 까지 측정된 스크린 타임으로 측정하겠다 하는 목표 마감 시간(--시--분), 이 시간 이후로 캡쳐된 사진만 업로드 허용",
            example = "20:30", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonFormat(pattern = "HH:mm")
    @NotNull
    private String deadLineTime;

    // 사용자가 정한 핸드폰 사용 시간
    @Schema(description = "사용자가 정한 핸드폰 사용 시간(-시간-분)", example = "05:00", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonFormat(pattern = "HH:mm")
    @NotNull
    private String goalTime;
}
