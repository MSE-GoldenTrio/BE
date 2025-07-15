package com.example.iplan.auth.oauth2;

import com.example.iplan.auth.UserRepository;
import com.example.iplan.auth.UserRole;
import com.example.iplan.auth.Users;
import com.example.iplan.auth.jwt.JwtToken;
import com.example.iplan.auth.jwt.JwtTokenProvider;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
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
    private boolean isNewUser; // 추가 정보 입력 여부 확인

    /**
     *  OAuth2 인증 완료 후, 시큐리티가 loadUser()를 호출하여 사용자 정보 가져옴
     */
    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) {
        log.info("OAuth2 Login Start: " + userRequest.getClientRegistration().getRegistrationId());

        OAuth2User oAuth2User = super.loadUser(userRequest);
        String registrationId = userRequest.getClientRegistration().getRegistrationId();

        // 플랫폼별 사용자 정보 매핑
        // OAuth2UserInfo.of()를 호출하여 표준화된 사용자 정보로 변환
        OAuth2UserInfo oAuth2UserInfo = OAuth2UserInfo.of(registrationId, oAuth2User.getAttributes());
        log.info("OAuth2 User Info: email={}, name={}", oAuth2UserInfo.getEmail(), oAuth2UserInfo.getName());

        // ** email 이 없으면 오류 발생시키기**
        if (oAuth2UserInfo.getEmail() == null || oAuth2UserInfo.getEmail().isEmpty()) {
            throw new IllegalArgumentException("OAuth2 로그인 실패: 이메일이 없습니다.");
        }

        // 사용자 저장 또는 업데이트 -> user 설정(반환)
        saveOrUpdate(oAuth2UserInfo);
        if (user == null) {
            throw new RuntimeException("OAuth2 로그인 중 사용자 정보가 정상적으로 저장되지 않았습니다.");
        }

        // JWT 발급 (닉네임을 랜덤으로 지정한 후 바로 발급)
//        JwtToken jwtToken = generateJwtToken(user);
//        log.info("OAuth2 Login JWT Token: {}", jwtToken.getAccessToken());

        return new CustomOAuth2UserDetails(user, oAuth2User.getAttributes());
    }

    /**
     * 디비에서 사용자 정보를 조회하고, 없으면 새로 저장
     */
    private void saveOrUpdate(OAuth2UserInfo oAuth2UserInfo) {
        try {
            // 이메일로 사용자 조회
            Optional<Users> existingUser = userRepository.findByEmail(oAuth2UserInfo.getEmail());
            if (existingUser.isPresent()) {
                // 1. 기존 회원이 로그인하는 경우
                user = existingUser.get();

                // 일반 로그인 사용자라면 소셜 로그인 거부 -> 비밀번호가 null 이 아님
                if (user.getPassword() != null && !user.getPassword().isEmpty()) {
                    throw new IllegalArgumentException("이미 일반 회원가입된 이메일입니다. 이메일/비밀번호로 로그인하세요.");
                }
                isNewUser = false; // 기존 회원
                log.info("소셜 로그인: 기존 유저 {}", user.getEmail());
            } else {
                // 2. 신규 회원이 로그인하는 경우 -> 랜덤 닉네임 생성 후 저장
                if (oAuth2UserInfo.getEmail() == null || oAuth2UserInfo.getEmail().isEmpty()) {
                    throw new IllegalArgumentException("OAuth2 로그인 실패: 이메일이 없습니다.");
                }
                String randomNickname = "user_" + UUID.randomUUID().toString().substring(0, 6);
                user = Users.builder()
                        .email(oAuth2UserInfo.getEmail())
                        .name(oAuth2UserInfo.getName())
                        .nickname(randomNickname) // 랜덤 닉네임 할당
                        .password("")   // 소셜 로그인은 비밀번호 없음
                        .authority(UserRole.UNKNOWN) // UserRole 타입으로 직접 전달, 아직 권한 미지정
                        .linked_id(new ArrayList<>()) // 빈 리스트로 초기화
                        .build();
                userRepository.saveWithAutoIncrement(user);

                isNewUser = true; // 신규 회원
                log.info("신규 소셜 사용자 등록: {}", user.getEmail());
            }
        } catch (ExecutionException | InterruptedException e) {
            log.error("Firestore error: ", e);
            throw new RuntimeException("서버 오류로 인해 로그인에 실패했습니다.");
        }
    }

    /**
     * 사용자 정보를 기반으로 JWT 토큰을 생성
     */
    private JwtToken generateJwtToken(Users user) {
        CustomOAuth2UserDetails customUserDetails = new CustomOAuth2UserDetails(user);

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                customUserDetails, null, customUserDetails.getAuthorities()
        );

        return jwtTokenProvider.generateToken(authentication);
    }

}
