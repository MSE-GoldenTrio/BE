package com.example.iplan.config.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.GenericFilterBean;

import java.io.IOException;

// 클라이언트로부터 들어오는 요청에서 JWT 토큰을 처리
// 유효한 토큰인 경우 해당 토큰의 인증 정보(Authentication)를 SecurityContext에 저장
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends GenericFilterBean {
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        log.info("Checking JWT token in JwtAuthenticationFilter...");

        // Swagger UI 경로는 JWT 토큰 검증을 하지 않도록 예외 처리
        String path = ((HttpServletRequest) request).getRequestURI();
        if (path.startsWith("/swagger-ui/**") || path.startsWith("/v3/api-docs")) {
            chain.doFilter(request, response);
            return;
        }

        // 1. Request Header 에서 JWT 토큰 추출
        String token = resolveToken((HttpServletRequest) request);
        log.info("JWT token: {}", token);

        // 2.  validateToken 으로 JWT 토큰 유효성 검사
        if (token != null && jwtTokenProvider.validateToken(token)) {
            log.info("JWT Token is valid");

            // 3. 토큰이 유효할 경우 토큰에서 사용자 정보를 가져와 CustomUserDetails 객체로 변환
            Authentication authentication = jwtTokenProvider.getAuthentication(token); // CustomUserDetails 객체 반환 -> Authentication

            // 4. Authentication 객체를 SecurityContext 에 저장
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        chain.doFilter(request, response);
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

