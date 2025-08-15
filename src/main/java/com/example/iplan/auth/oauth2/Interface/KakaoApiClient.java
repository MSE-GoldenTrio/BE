package com.example.iplan.auth.oauth2.Interface;

import lombok.Data;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.util.MultiValueMap;

@FeignClient(name = "kakaoApiClient", url = "https://kapi.kakao.com")
public interface KakaoApiClient {

    /**
     * 카카오 어드민키로 소셜 연동 해제
     * @param adminAuth
     * @param form
     * @return
     */
    @PostMapping(value = "/v1/user/unlink",
            consumes = org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    Long unlinkByAdmin(
            @RequestHeader("Authorization") String adminAuth, // "KakaoAK {ADMIN_KEY}"
            @RequestBody MultiValueMap<String,String> form
            // form: target_id_type=user_id, target_id={숫자}
    );

    /**
     * access token 방식으로 소셜 연동 해제
     * @param bearer
     * @param form
     * @return
     */
    @PostMapping(value = "/v1/user/unlink",
            consumes = org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    Long unlinkByAccess(
            @RequestHeader("Authorization") String bearer, // "Bearer {accessToken}"
            @RequestBody MultiValueMap<String,String> form // 비워도 됨
    );
}


