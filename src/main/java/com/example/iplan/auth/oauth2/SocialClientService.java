package com.example.iplan.auth.oauth2;

import com.example.iplan.auth.oauth2.DTO.KakaoTokenResponse;
import com.example.iplan.auth.oauth2.DTO.NaverTokenResponse;
import com.example.iplan.auth.oauth2.Interface.KakaoApiClient;
import com.example.iplan.auth.oauth2.Interface.KakaoAuthClient;
import com.example.iplan.auth.oauth2.Interface.NaverClient;
import com.example.iplan.auth.oauth2.Interface.GoogleClient;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@Slf4j
@RequiredArgsConstructor
@Service
public class SocialClientService {

    private final GoogleClient googleClient;
    private final KakaoAuthClient kakaoAuthClient;
    private final KakaoApiClient kakaoApiClient;
    private final NaverClient naverClient;

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String googleClientId;

    @Value("${spring.security.oauth2.client.registration.google.client-secret}")
    private String googleClientSecret;

    @Value("${spring.security.oauth2.client.registration.kakao.client-id}")
    private String kakaoClientId;

    @Value("${spring.security.oauth2.client.registration.kakao.client-secret:}")
    private String kakaoClientSecret;

    @Value("${spring.security.oauth2.client.registration.naver.client-id}")
    private String naverClientId;

    @Value("${spring.security.oauth2.client.registration.naver.client-secret}")
    private String naverClientSecret;

    /**
     * Google 토큰 갱신 요청
     * @param refreshToken
     * @return
     */
    public GoogleTokenResponse reissueGoogleToken(String refreshToken) {
        var form = new LinkedMultiValueMap<String,String>();

        form.add("grant_type", "refresh_token");
        form.add("client_id", googleClientId);
        form.add("client_secret", googleClientSecret);
        form.add("refresh_token", refreshToken);

        return googleClient.reissueToken(form);
    }

    /**
     * Google 연결 해제 요청
     * @param token
     */
    public void unlinkGoogle(String token) {
        var form = new LinkedMultiValueMap<String,String>();
        form.add("token", token); // refresh 권장

        try {
            googleClient.revoke(form);
            log.info("Google unlink 요청 완료");

        } catch (feign.FeignException e) {
            log.warn("Google unlink 실패: status={}, body={}", e.status(), e.contentUTF8());
            // 로컬 언링크는 계속 진행하도록 상위에서 분기
        }
    }

    /**
     * Kakao 토큰 갱신 요청
     * @param refreshToken
     * @return
     */
    public KakaoTokenResponse reissueKakaoToken(String refreshToken) {
        var form = new LinkedMultiValueMap<String,String>();
        form.add("grant_type", "refresh_token");
        form.add("client_id", kakaoClientId);

        if (kakaoClientSecret != null && !kakaoClientSecret.isBlank())
            form.add("client_secret", kakaoClientSecret);

        form.add("refresh_token", refreshToken);

        return kakaoAuthClient.reissueToken(form);
    }

    /**
     * 카카오 어드민키로 소셜 연동 해제
     * @param kakaoUserId
     * @return
     */
/*    public Long unlinkKakaoByAdmin(long kakaoUserId) {
        var form = new LinkedMultiValueMap<String,String>();
        form.add("target_id_type", "user_id");
        form.add("target_id", String.valueOf(kakaoUserId));

        try {
            var res = kakaoApiClient.unlinkByAdmin("KakaoAK " + kakaoAdminKey, form);
            log.info("Kakao unlink(Admin) 성공: id={}", res);
            return res;

        } catch (feign.FeignException e) {
            log.warn("Kakao unlink(Admin) 실패: status={}, body={}", e.status(), e.contentUTF8());
            return null;
        }
    }*/

    /**
     * Kakao 연결 해제 요청
     * @param accessToken
     */
    public Long unlinkKakaoByAccess(String accessToken) {
        var form = new org.springframework.util.LinkedMultiValueMap<String,String>();

        try {
            var res = kakaoApiClient.unlinkByAccess("Bearer " + accessToken, form);
            log.info("Kakao unlink(Access) 성공: id={}", res);
            return res;

        } catch (feign.FeignException e) {
            log.warn("Kakao unlink(Access) 실패: status={}, body={}", e.status(), e.contentUTF8());
            return null;
        }
    }

    public NaverTokenResponse reissueNaverToken(String refreshToken) {
        var form = new LinkedMultiValueMap<String,String>();

        form.add("grant_type", "refresh_token");
        form.add("client_id", naverClientId);
        form.add("client_secret", naverClientSecret);
        form.add("refresh_token", refreshToken);

        return naverClient.reissueToken(form);
    }

    public boolean unlinkNaver(String accessToken){
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "delete");
        form.add("client_id", naverClientId);
        form.add("client_secret", naverClientSecret);
        form.add("access_token", accessToken);
        form.add("service_provider", "NAVER");

        var res = naverClient.unlink(form);
        boolean ok = "success".equalsIgnoreCase(res);
        if (ok) {
            log.info("NAVER unlink success");
        } else {
            log.warn("NAVER unlink failed: error={}", res);
        }
        return ok;
    }

}
