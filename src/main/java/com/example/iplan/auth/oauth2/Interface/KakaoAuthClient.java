package com.example.iplan.auth.oauth2.Interface;

import com.example.iplan.auth.oauth2.DTO.KakaoTokenResponse;
import com.google.api.client.auth.oauth2.TokenResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "kakaoAuthClient", url = "https://kauth.kakao.com")
public interface KakaoAuthClient {

    @PostMapping(value = "/oauth/token",
            consumes = org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    KakaoTokenResponse reissueToken(@RequestBody MultiValueMap<String,String> form);
    // form: grant_type=refresh_token, client_id, client_secret(필요 시), refresh_token
}
