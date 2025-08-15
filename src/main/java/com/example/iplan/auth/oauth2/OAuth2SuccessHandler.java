package com.example.iplan.auth.oauth2;

import com.example.iplan.auth.UserRepository;
import com.example.iplan.auth.UserService;
import com.example.iplan.auth.Users;
import com.example.iplan.auth.jwt.JwtProperties;
import com.example.iplan.auth.jwt.JwtToken;
import com.example.iplan.auth.jwt.JwtTokenProvider;
import com.example.iplan.auth.redis.OAuth2ProviderTokenService;
import com.example.iplan.auth.redis.RefreshToken;
import com.example.iplan.auth.redis.RefreshTokenService;
import com.example.iplan.util.AES256Encryptor;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.UserRecord;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * OAuth2 로그인 성공 후 JWT 발급 및 React Native 앱으로 리다이렉트 처리
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final UserRepository userRepository;
    private final AES256Encryptor aes;
    private final JwtProperties jwtProperties;
    private final RefreshTokenService refreshTokenService;

    private final OAuth2AuthorizedClientService authorizedClientService;
    private final OAuth2ProviderTokenService providerTokenService;

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

        // Firebase Authentication 사용자 생성 또는 가져오기
        try {
            UserRecord userRecord;
            try {
                // 이메일로 이미 존재하는지 확인
                userRecord = FirebaseAuth.getInstance().getUserByEmail(aes.decrypt(user.getEmail()));
                log.info("기존 Firebase Authentication 사용자: {}", userRecord.getUid());
            } catch (Exception e) {
                // 존재하지 않으면 새로 생성 (UID 자등오르 랜덤 생성됨)
                UserRecord.CreateRequest createRequest = new UserRecord.CreateRequest()
                        .setEmail(aes.decrypt(user.getEmail()))
                        .setDisplayName(aes.decrypt(user.getName()));

                userRecord = FirebaseAuth.getInstance().createUser(createRequest);
                log.info("새 Firebase Authentication 사용자 생성됨: {}", userRecord.getUid());

                // UID 사용자 정보 업데이트
                user.setFirebaseAuthUID(userRecord.getUid());
                userRepository.update(user);
                log.info("UID 업데이트 성공: {}", userRecord.getUid());
            }
        } catch (Exception e) {
            log.error("Firebase 사용자 생성 중 오류", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Firebase 사용자 등록 중 오류 발생");
            return;
        }

        // JWT 발급
        JwtToken jwtToken = null;
        try {
            jwtToken = jwtTokenProvider.generateToken(authentication);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Refresh 토큰 Redis 에 저장
        long expirationMinutes = jwtProperties.getRefreshTokenExpiration() / 1000 / 60; // ms → minutes
        refreshTokenService.saveToken(
                (CustomOAuth2UserDetails) authentication.getPrincipal(),
                jwtToken.getRefreshToken(),
                expirationMinutes
        );
        log.info("Refresh 토큰 Redis 에 저장: user_id={}, ttl={}min", user.getNickname(), expirationMinutes);

        // React Native 앱으로 리다이렉트 (딥링크 사용)
        String redirectUrl = String.format("iplan://auth-callback?accessToken=%s&refreshToken=%s&needsAdditionalInfo=%s",
                URLEncoder.encode(jwtToken.getAccessToken(), StandardCharsets.UTF_8),
                URLEncoder.encode(jwtToken.getRefreshToken(), StandardCharsets.UTF_8),
                isNewUser);

        log.info("OAuth2 Redirect to : {}", redirectUrl);
        response.sendRedirect(redirectUrl);

        // 새로 추가!!
        // OAuth2 토큰을 AuthorizedClient에서 꺼내 Redis 저장
        if(authentication instanceof OAuth2AuthenticationToken oauthToken){
            String registrationId = oauthToken.getAuthorizedClientRegistrationId(); //google, kakao, naver
            OAuth2AuthorizedClient client =
                    authorizedClientService.loadAuthorizedClient(registrationId, oauthToken.getName());

            if(client != null){
                OAuth2AccessToken accessToken = client.getAccessToken();
                OAuth2RefreshToken refreshToken = client.getRefreshToken();

                String subject = user.getEmailHash();

                if(accessToken != null && accessToken.getTokenValue() != null){
                    Instant now = Instant.now();
                    Instant exp = Objects.requireNonNullElse(accessToken.getExpiresAt(), now.plusSeconds(3600));
                    long ttlSec = Math.max(10, exp.getEpochSecond() - now.getEpochSecond());
                    providerTokenService.saveAccessToken(registrationId, subject,accessToken.getTokenValue(), Duration.ofSeconds(ttlSec));
                }
                if(refreshToken != null && refreshToken.getTokenValue() != null){
                    // 구글은 refresh 명시 만료 없음 ->  넉넉히, 카카오는 refresh_token_expires_in 활용가능
                    providerTokenService.saveRefreshToken(registrationId,subject,refreshToken.getTokenValue(), Duration.ofDays(180));
                }
                log.info("Saved OAuth2 tokens to Redis. provider={}, subject={}", registrationId, subject);
            }else{
                log.warn("AuthorizedClient not found. provider={}", oauthToken.getAuthorizedClientRegistrationId());
            }
        }
    }
}
