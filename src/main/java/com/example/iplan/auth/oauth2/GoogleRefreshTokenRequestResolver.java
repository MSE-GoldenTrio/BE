package com.example.iplan.auth.oauth2;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;

import java.util.HashMap;
import java.util.Map;

public class GoogleRefreshTokenRequestResolver implements OAuth2AuthorizationRequestResolver {

    private final DefaultOAuth2AuthorizationRequestResolver delegate;

    public GoogleRefreshTokenRequestResolver(ClientRegistrationRepository repo, String authorizationRequestBaseUri) {
        this.delegate = new DefaultOAuth2AuthorizationRequestResolver(repo, authorizationRequestBaseUri);
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        return customize(delegate.resolve(request));
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        return customize(delegate.resolve(request, clientRegistrationId));
    }

    private OAuth2AuthorizationRequest customize(OAuth2AuthorizationRequest req){
        if(req == null) return null;

        String registrationId = req.getAttribute(OAuth2ParameterNames.REGISTRATION_ID);
        if(!"google".equals(registrationId)){
            return req; // 구글 외에는 그대로
        }

        // 기존 파라미터 + 추가 파라미터 병합
        Map<String, Object> extra = new HashMap<>(req.getAdditionalParameters());
        extra.put("access_type", "offline"); // refresh_token 발급 트리거
        extra.put("prompt", "consent"); // 이미 동의한 사용자도 재동의 유도(최초 1회 보장)

        return OAuth2AuthorizationRequest.from(req)
                .additionalParameters(extra)
                .build();
    }
}
