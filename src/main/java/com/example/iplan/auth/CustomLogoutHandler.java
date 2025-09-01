package com.example.iplan.auth;

import com.example.iplan.ExceptionHandler.CustomException;
import com.example.iplan.fcm.FcmTokenService;
import com.example.iplan.auth.jwt.JwtTokenProvider;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class CustomLogoutHandler implements LogoutHandler, LogoutSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final FcmTokenService fcmTokenService;
    private final UserRepository userRepository;

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        log.info("로그아웃 요청 수신");

        // Authorization 헤더에서 추출
        String header = request.getHeader("Authorization"); // accessToken
        String fcmToken = request.getHeader("fcm-token");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            String userId = jwtTokenProvider.getUserNickname(token);
            Users user = userRepository.findByHashValueNickName(DigestUtils.sha256Hex(userId)).orElseThrow(() -> new IllegalArgumentException("User not found"));

            // FCM 토큰 삭제
            if (fcmToken != null && !fcmToken.isBlank()) {
                fcmTokenService.deleteToken(user.getNicknameHash(), fcmToken);
            }

            // 토큰 무효화 처리
            jwtTokenProvider.destroyToken(token, "logout", fcmToken);
            log.info("토큰 무효화 처리 완료 (Redis Blacklist 등록)");
        } else {
            throw new CustomException("일시적 오류가 발생하였습니다.", "로그아웃 오류: Authorization 헤더가 없거나 형식이 잘못되었습니다.", HttpStatus.BAD_REQUEST, null);
        }
    }

    @Override
    public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response,
                                Authentication authentication) throws IOException, ServletException {
        log.info("로그아웃 성공 응답 반환");

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"message\": \"Successfully logged out.\"}");
    }
}
