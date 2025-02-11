package com.example.iplan.Domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.google.cloud.firestore.annotation.DocumentId;
import lombok.*;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
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

    private String user_id;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private String date;

    private Map<String, Object> result;

    private boolean isSuccess;
}
