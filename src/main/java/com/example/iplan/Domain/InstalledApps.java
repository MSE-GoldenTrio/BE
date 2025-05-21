package com.example.iplan.Domain;

import com.google.cloud.firestore.annotation.DocumentId;
import com.google.firebase.database.annotations.NotNull;
import lombok.*;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InstalledApps {

    @DocumentId
    private String id;

    @NotNull
    private String user_id;

    @NotNull
    private List<String> installed_apps;
}
