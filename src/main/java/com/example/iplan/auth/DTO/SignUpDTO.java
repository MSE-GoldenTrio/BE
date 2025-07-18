package com.example.iplan.auth.DTO;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class SignUpDTO {
    private String nickname;
    private String password;
    private String name;
    private String authority;
    private String idToken;     // 파이어베이스 이메일 본인인증 후 받아온 토큰 -> 여기서 이메일 추출
}
