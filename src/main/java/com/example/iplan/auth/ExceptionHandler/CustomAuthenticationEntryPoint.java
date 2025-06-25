package com.example.iplan.auth.ExceptionHandler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.ServletException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException, ServletException {

        log.warn("401 에러: 인증 없이 접근 - {}", authException.getMessage());

        // 기본 메시지
        String errorMessage = "인증이 필요합니다.";

        // CustomAuthenticationException인 경우 message 덮어쓰기
        if (authException instanceof CustomAuthenticationException) {
            errorMessage = authException.getMessage();
        }

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // 401
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"message\": \"" + errorMessage + "\"}");
    }
}
