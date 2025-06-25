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

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String path = httpRequest.getRequestURI();

        // Swagger UI 경로는 JWT 토큰 검증을 하지 않도록 예외 처리
        if (path.startsWith("/swagger-ui/**") || path.startsWith("/v3/api-docs")) {
            chain.doFilter(request, response);
            return;
        }

        // 1. Request Header 에서 JWT 토큰 추출
        String token = resolveToken(httpRequest);
        log.info("JWT token: {}", token);

        // 2. validateToken 으로 JWT 토큰 유효성 검사
        try {
            // 토큰이 있고 유효한 경우
            if (token != null) {
                if (jwtTokenProvider.validateToken(token)) {
                    log.info("JWT Token is valid");
                    Authentication authentication = jwtTokenProvider.getAuthentication(token);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } else {
                    // 토큰은 있는데 유효하지 않은 경우 → 401 응답
                    log.warn("JWT token is invalid");
                    HttpServletResponse httpResponse = (HttpServletResponse) response;
                    httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    httpResponse.getWriter().write("Unauthorized: Invalid or expired token");
                    return;
                }
            }
        } catch (Exception e) {
            log.error("JWT Authentication error: {}", e.getMessage());
            HttpServletResponse httpResponse = (HttpServletResponse) response;
            httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            httpResponse.getWriter().write("Unauthorized: Token processing failed");
            return;
        }
        // 토큰이 없거나 유효한 경우 → 다음 필터로 넘김
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

