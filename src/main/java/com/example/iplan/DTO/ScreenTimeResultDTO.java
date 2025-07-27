package com.example.iplan.DTO;

import com.example.iplan.Domain.ScreenTimeOCRResult;
import com.example.iplan.util.AES256Encryptor;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.firebase.database.annotations.NotNull;
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

    public static ScreenTimeResultDTO fromEntity(ScreenTimeOCRResult result, AES256Encryptor aes) throws Exception {
        return ScreenTimeResultDTO.builder()
                .id(result.getId())
                .user_id(result.getUser_id())
                .date(result.getDate())
                .result(aes.decryptJsonObject(result.getResult(), new TypeReference<>() {}))
                .isSuccess(result.isSuccess())
                .build();
    }
}
