package com.example.iplan.Domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.google.cloud.firestore.annotation.DocumentId;
import com.google.firebase.database.annotations.NotNull;
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

    private Map<String, Object> result;

    private boolean isSuccess;
}
