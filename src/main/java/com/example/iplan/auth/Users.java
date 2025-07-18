package com.example.iplan.auth;

import com.google.cloud.firestore.annotation.DocumentId;
import com.google.firebase.database.annotations.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
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

    @Schema(description = "사용자 아이디", example = "alice", requiredMode = Schema.RequiredMode.REQUIRED)
    private String nickname;

    @Schema(description = "사용자 아이디 해시값, 중복 비교용")
    private String nicknameHash;

    @Schema(description = "사용자 이메일", example = "abcd@gmail.com", requiredMode = Schema.RequiredMode.REQUIRED)
    private String email;

    private String emailHash;

    @NotNull
    @Schema(description = "사용자 비밀번호", example = "dlkjeigoidjlkajlckd", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;

    @NotNull
    @Schema(description = "사용자 이름", example = "홍길동", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @NotNull
    @Schema(description = "사용자 권한(아이/부모)", example = "CHILD", requiredMode = Schema.RequiredMode.REQUIRED)
    private UserRole authority;

    @Schema(description = "사용자와 연동된 ID 목록", example = "[\"child1\", \"child2\"]")
    private List<String> linked_id = new ArrayList<>();

    @Schema(description = "사용자 기기 토큰 값", example = "abc456")
    private String fcmToken = null;

    @Schema(description = "어떤 소셜 플랫폼인지에 대한 정보", example = "google")
    private String provider;

    @Schema(description = "소셜 플랫폼에서 받은 access token")
    private String providerAccessToken;
}
