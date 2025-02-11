package com.example.iplan.auth.oauth2;

import com.example.iplan.auth.CustomUserDetails;
import com.example.iplan.auth.UserRepository;
import com.example.iplan.auth.UserRole;
import com.example.iplan.auth.Users;
import com.example.iplan.config.jwt.JwtToken;
import com.example.iplan.config.jwt.JwtTokenProvider;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;

    @Getter
    private Users user;  // 현재 로그인한 사용자 정보 저장
    @Getter
    private boolean isNewUser; // 새로운 사용자 여부 확인

    /**
     *  OAuth2 인증 완료 후, 시큐리티가 loadUser()를 호출하여 사용자 정보 가져옴
     */
    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) {
        log.info("OAuth2 Login Start" + userRequest.getClientRegistration().getRegistrationId());

        OAuth2User oAuth2User = super.loadUser(userRequest);
        String registrationId = userRequest.getClientRegistration().getRegistrationId();

        // 플랫폼별 사용자 정보 매핑
        // OAuth2UserInfo.of()를 호출하여 표준화된 사용자 정보로 변환
        OAuth2UserInfo oAuth2UserInfo = OAuth2UserInfo.of(registrationId, oAuth2User.getAttributes());
        log.info("OAuth2 User Info: " + oAuth2UserInfo.getEmail());

        // 사용자 저장 또는 업데이트
        saveOrUpdate(oAuth2UserInfo);

        // JWT 토큰 생성
        JwtToken jwtToken = generateJwtToken(user);
        log.info("OAuth2 Login JWT Token: {}", jwtToken.getAccessToken());

        // CustomOAuth2UserDetails 사용하여 OAuth2User 반환
        return new CustomOAuth2UserDetails(user, oAuth2User.getAttributes());
    }

    /**
     * 디비에서 사용자 정보를 조회하고, 없으면 새로 저장
     */
    private void saveOrUpdate(OAuth2UserInfo oAuth2UserInfo) {
        try {
            Optional<Users> existingUser = userRepository.findByEmail(oAuth2UserInfo.getEmail());
            if (existingUser.isPresent()) {
                user = existingUser.get();
                log.info("User already exists: " + user.getEmail());
            } else {
                user = oAuth2UserInfo.toEntity();
                userRepository.save(user);
                log.info("User saved successfully: " + user.getEmail());
            }
        } catch (ExecutionException | InterruptedException e) {
            log.error("Firestore error: ", e);
        }
    }


    /**
     * 사용자 정보를 기반으로 JWT 토큰을 생성
     */
    private JwtToken generateJwtToken(Users user) {
        CustomUserDetails customUserDetails = CustomUserDetails.builder()
                .username(user.getEmail())
                .password("") // OAuth2 로그인 사용자는 비밀번호 없음
                .role(UserRole.fromString(user.getAuthority())) // 권한 Enum 으로 변경
                .build();

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                customUserDetails, null, customUserDetails.getAuthorities()
        );

        return jwtTokenProvider.generateToken(authentication);
    }
}
