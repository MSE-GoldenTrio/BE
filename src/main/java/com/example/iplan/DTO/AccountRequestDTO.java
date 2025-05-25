package com.example.iplan.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import org.springframework.stereotype.Component;

@Component
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "계정 연동 요청 DTO")
public class AccountRequestDTO {
    private String id;
    private String childNickname;
    private String parentNickname;
    private boolean approved; // 자녀가 승인했는지 여부
}
