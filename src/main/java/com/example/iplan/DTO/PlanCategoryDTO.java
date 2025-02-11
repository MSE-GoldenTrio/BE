package com.example.iplan.DTO;

import com.google.cloud.firestore.annotation.DocumentId;
import com.google.firebase.database.annotations.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class PlanCategoryDTO {

    private String id;
    private String user_id;

    @NotNull
    private String name;
}
