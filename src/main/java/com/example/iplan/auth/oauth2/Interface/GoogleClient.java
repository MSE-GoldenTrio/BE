package com.example.iplan.auth.oauth2.Interface;

import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.util.MultiValueMap;

@FeignClient(name = "googleClient", url = "https://oauth2.googleapis.com")
public interface GoogleClient {

    @PostMapping(value = "/token",
            consumes = org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    GoogleTokenResponse reissueToken(@RequestBody MultiValueMap<String,String> form);
    // form: grant_type=refresh_token, client_id, client_secret, refresh_token

    @PostMapping(value = "/revoke",
            consumes = org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    feign.Response revoke(@RequestBody MultiValueMap<String,String> form);
    // form: token=<refresh 또는 access>
}
