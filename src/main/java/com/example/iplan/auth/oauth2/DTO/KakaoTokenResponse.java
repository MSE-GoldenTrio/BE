package com.example.iplan.auth.oauth2.DTO;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class KakaoTokenResponse {

    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("token_type")
    private String tokenType;

    @JsonProperty("expires_in")
    private Long expiresIn;

    @JsonProperty("refresh_token")
    private String refreshToken; // 내려올 수도 있음

    @JsonProperty("refresh_token_expires_in")
    private Long refreshTokenExpiresIn;

    private String scope;
}
