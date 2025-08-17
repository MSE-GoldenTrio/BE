package com.example.iplan.auth.oauth2.Interface;

import com.example.iplan.auth.oauth2.DTO.NaverTokenResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "naverClient", url = "https://nid.naver.com")
public interface NaverClient {
    @PostMapping(value = "/oauth2.0/token",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    NaverTokenResponse reissueToken(@RequestBody MultiValueMap<String,String> form);
    // form: grant_type=refresh_token, client_id, client_secret, refresh_token

    @PostMapping(value = "/oauth2.0/token",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    String unlink(@RequestBody MultiValueMap<String,String> form);
    // form: grant_type=delete, client_id, client_secret, access_token, service_provider=NAVER
}
