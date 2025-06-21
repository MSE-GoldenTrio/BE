package com.example.iplan.auth.jwt;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@AllArgsConstructor
@Builder
public class JwtToken {
    private String grantType; //Bearer
    private String accessToken;
    private String refreshToken;
}
