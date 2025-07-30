package com.example.iplan.DTO;

import com.example.iplan.Domain.ScreenTimeOCRResult;
import com.example.iplan.util.AES256Encryptor;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.cloud.Timestamp;
import com.google.firebase.database.annotations.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScreenTimeResultDTO {

    private String id;

    @NotNull
    private String user_id;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private String date;

    private Map<String, Object> result;

    private boolean isSuccess;

    @Schema(description = "문서 최종 수정 시간")
    @NotNull
    private Timestamp updated_at;

    public static ScreenTimeResultDTO fromEntity(ScreenTimeOCRResult result, AES256Encryptor aes) throws Exception {
        return ScreenTimeResultDTO.builder()
                .id(result.getId())
                .user_id(aes.decrypt(result.getUser_id()))
                .date(result.getDate())
                .result(aes.decryptJsonObject(result.getResult(), new TypeReference<>() {}))
                .isSuccess(result.isSuccess())
                .updated_at(result.getUpdated_at())
                .build();
    }
}
