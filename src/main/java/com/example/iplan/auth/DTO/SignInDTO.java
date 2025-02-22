package com.example.iplan.auth.DTO;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class SignInDTO {
    private String nickname;
    private String password;
    private String fcmToken;    // 사용자가 직접 입력 x
}
