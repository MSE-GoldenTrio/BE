package com.example.iplan.auth.oauth2;

import com.example.iplan.auth.Users;
import com.example.iplan.auth.jwt.JwtToken;
import com.example.iplan.auth.jwt.JwtTokenProvider;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * OAuth2 로그인 성공 후 JWT 발급 및 React Native 앱으로 리다이렉트 처리
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomOAuth2UserService customOAuth2UserService;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        log.info("소셜로그인 성공: {}", authentication.getName());

        // 사용자 정보 가져오기
        Users user = customOAuth2UserService.getUser();
        boolean isNewUser = customOAuth2UserService.isNewUser();

        if (user == null) {
            log.error("OAuth2 login error: No user info");
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "OAuth2 로그인 중 사용자 정보가 없습니다.");
            return;
        }

        // JWT 발급
        JwtToken jwtToken = jwtTokenProvider.generateToken(authentication);
        log.info("OAuth2 Access Token: {}", jwtToken.getAccessToken());
        log.info("OAuth2 Refresh Token: {}", jwtToken.getRefreshToken());

        // React Native 앱으로 리다이렉트 (딥링크 사용)
        String redirectUrl = String.format("iplan://auth-callback?accessToken=%s&refreshToken=%s&needsAdditionalInfo=%s",
                URLEncoder.encode(jwtToken.getAccessToken(), StandardCharsets.UTF_8),
                URLEncoder.encode(jwtToken.getRefreshToken(), StandardCharsets.UTF_8),
                isNewUser);

        log.info("OAuth2 Redirect to : {}", redirectUrl);
        response.sendRedirect(redirectUrl);
    }
}
