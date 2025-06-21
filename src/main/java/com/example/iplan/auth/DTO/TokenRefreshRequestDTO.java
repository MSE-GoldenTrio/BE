package com.example.iplan.auth.DTO;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class TokenRefreshRequestDTO {
    private String accessToken;
    private String refreshToken;
}
