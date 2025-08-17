package com.example.iplan.auth.oauth2.DTO;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class NaverTokenResponse {

    @JsonProperty("access_token") private String accessToken;

    @JsonProperty("expires_in") private String expiresIn;

    @JsonProperty("refresh_token") private String refreshToken;

    @JsonProperty("token_type") private String tokenType;

    private String scope;
}
