package com.example.iplan.Domain;

import com.example.iplan.DTO.ScreenTimeResultDTO;
import com.example.iplan.util.AES256Encryptor.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.annotation.DocumentId;
import com.google.firebase.database.annotations.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScreenTimeOCRResult {

    @DocumentId
    private String id;

    @NotNull
    private String user_id;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private String date;

    private String result;

    private boolean isSuccess;

    @Schema(description = "문서 최종 수정 시간")
    @NotNull
    private Timestamp updated_at;
}
