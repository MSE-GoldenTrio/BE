package com.example.iplan.auth;

import com.google.cloud.firestore.annotation.DocumentId;
import com.google.firebase.database.annotations.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "사용자 정보 엔티티")
public class Users {
    @DocumentId
    @Schema(description = "사용자 고유 ID", example = "abc123")
    private String id; // Firestore 문서의 ID

    @NotNull
    @Schema(description = "사용자 이메일", example = "abcd@gmail.com", requiredMode = Schema.RequiredMode.REQUIRED)
    private String email;

    @NotNull
    @Schema(description = "사용자 비밀번호", example = "dlkjeigoidjlkajlckd", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;

    @NotNull
    @Schema(description = "사용자 이름", example = "홍길동", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @NotNull
    @Schema(description = "사용자 권한(아이/부모)", example = "CHILD", requiredMode = Schema.RequiredMode.REQUIRED)
    private String authority;

    @Schema(description = "사용자와 연동된 id", example = "abc456")
    private String linked_id = null;

    @Schema(description = "사용자 기기 토큰 값", example = "abc456")
    private String fcmToken = null;
}
