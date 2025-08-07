package com.example.iplan.auth.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.core.AuthenticationException;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.GenericFilterBean;

import java.io.IOException;

// 클라이언트로부터 들어오는 요청에서 JWT 토큰을 처리
// 유효한 토큰인 경우 해당 토큰의 인증 정보(Authentication)를 SecurityContext에 저장
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends GenericFilterBean {
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationEntryPoint authenticationEntryPoint;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        log.info("Checking JWT token in JwtAuthenticationFilter...");

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        String path = httpRequest.getRequestURI();

        // Swagger UI 경로는 JWT 토큰 검증을 하지 않도록 예외 처리
        if (path.startsWith("/swagger-ui/**") || path.startsWith("/v3/api-docs")) {
            chain.doFilter(request, response);
            return;
        }
        try {
            // 1. Request Header 에서 JWT 토큰 추출
            String token = resolveToken(httpRequest);
            log.info("JWT token: {}", token);

            // 2. validateToken 으로 JWT 토큰 유효성 검사
            // 이때 예외 발생 시 오류(CustomAuthenticationException)를 직접 catch 하지 않고, 예외를 던지면 (Custom)AuthenticationEntryPoint 가 처리함
            if (token != null) {
                // AccessToken 유효성 + 블랙리스트 검사 포함
                jwtTokenProvider.verifyAccessToken(token);  // 유효하지 않으면 예외 발생

                Authentication authentication = jwtTokenProvider.getAuthentication(token);
                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.info("Authentication set in SecurityContext");
            }

            // 토큰이 없거나 유효한 경우 → 다음 필터로 넘김
            chain.doFilter(request, response);
        } catch (AuthenticationException ex) {
            // SecurityContext 초기화
            SecurityContextHolder.clearContext();
            // AuthenticationEntryPoint 직접 호출
            authenticationEntryPoint.commence(httpRequest, httpResponse, ex);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // Request Header 에서 토큰 추출
    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}

