package com.example.iplan.auth.oauth2;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class OAuth2FailureHandler implements AuthenticationFailureHandler {

    // 실패 시 리디렉션할 기본 URI
    private final String FAILURE_REDIRECT_URI = "iplan://auth-callback?error=";

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException, ServletException {

        String errorMessage = exception.getMessage();
        log.warn("OAuth2 로그인 실패: {}", errorMessage);

        // URL 인코딩
        String encodedError = URLEncoder.encode(errorMessage, StandardCharsets.UTF_8);
        String redirectUrl = FAILURE_REDIRECT_URI + encodedError;

        response.sendRedirect(redirectUrl);
    }
}
