package com.example.iplan.auth.oauth2;

import com.example.iplan.auth.UserRole;
import com.example.iplan.auth.Users;
import lombok.Builder;
import lombok.Getter;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
@Getter
@Builder
@AllArgsConstructor
public class OAuth2UserInfo {
    private String name;
    private String email;

    /**
     * OAuth2 로그인 후, 플랫폼별 사용자 정보를 추출하는 공통 클래스
     * OAuth2 제공업체에 따라 적절한 매핑 메서드를 호출하여 사용자 정보를 반환
     */
    public static OAuth2UserInfo of(String registrationId, Map<String, Object> attributes) {
        return switch (registrationId) {
            case "google" -> ofGoogle(attributes);
            case "naver" -> ofNaver(attributes);
            case "kakao" -> ofKakao(attributes);
            default -> throw new IllegalArgumentException("Unsupported OAuth2 Provider: " + registrationId);
        };
    }

    // 구글
    private static OAuth2UserInfo ofGoogle(Map<String, Object> attributes) {
        log.info("Google User Attributes: {}", attributes);  // Google 에서 오는 데이터를 확인
        String name = (String) attributes.get("name"); // 이름 가져오기
        String email = (String) attributes.get("email"); // 이메일 가져오기

        if (email == null || email.isEmpty()) {
            throw new IllegalArgumentException("Google OAuth2 로그인 실패: 이메일이 없습니다.");
        }

        return OAuth2UserInfo.builder()
                .name(name)
                .email(email)
                .build();
    }

    // 네이버
    private static OAuth2UserInfo ofNaver(Map<String, Object> attributes) {
        Map<String, Object> response = (Map<String, Object>) attributes.get("response");
        return OAuth2UserInfo.builder()
                .name((String) response.get("name"))
                .email((String) response.get("email"))
                .build();
    }

    // 카카오
    private static OAuth2UserInfo ofKakao(Map<String, Object> attributes) {
        Map<String, Object> account = (Map<String, Object>) attributes.get("kakao_account");
        Map<String, Object> profile = (Map<String, Object>) account.get("profile");

        return OAuth2UserInfo.builder()
                .name((String) profile.get("nickname"))
                .email((String) account.get("email"))
                .build();
    }

    /**
     * Firestore Users 엔티티로 변환
     */
    public Users toEntity() {
        return Users.builder()
                .name(name)
                .email(email)
                .password("") // OAuth2 로그인 사용자는 비밀번호 없음
                .authority(UserRole.CHILD.name()) // 기본 권한 설정
                .build();
    }

}
