package com.example.iplan.auth.oauth2;

import com.example.iplan.auth.Users;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * OAuth2 로그인 & 일반 로그인 둘 다 지원 가능!
 * JWT 인증 & OAuth2 인증 모두 사용 가능
 * CustomOAuth2UserService에서 OAuth2 로그인 시 자동으로 SecurityContext에 저장 가능
 */

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CustomOAuth2UserDetails implements OAuth2User, UserDetails {
    private Users user;
    private Map<String, Object> attributes;
    private String jwtToken;

    // 일반 로그인 사용자를 위한 생성자 (OAuth2 attributes 없이 생성)
    public CustomOAuth2UserDetails(Users user) {
        this.user = user;
        this.attributes = null; // 일반 로그인 사용자는 attributes 없음
        this.jwtToken = null;
    }

    // Spring Security의 권한 정보 반환
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singletonList(
                new SimpleGrantedAuthority(user.getAuthority())); // 사용자 권한
    }

    // 사용자 비밀번호 (OAuth2 로그인 시 null)
    @Override
    public String getPassword() {
        return user.getPassword();
    }

     // Spring Security 에서 사용할 사용자 이름 (닉네임)
     // Spring Security 의 인증 과정에서 사용자 식별을 위해 호출
     @Override
     public String getUsername() {
         return user.getNickname();
     }

    // OAuth2 로그인 시 제공된 사용자 속성
    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    // OAuth2User 에서 요구하는 getName()
    // OAuth2 로그인 시 사용자의 고유 식별자를 반환할 때 호출됨
    @Override
    public String getName() {
        return user.getNickname();
    }

    // 계정 만료 여부
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    // 계정 잠금 여부
    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    // 자격 증명 만료 여부
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    // 계정 활성화 여부
    @Override
    public boolean isEnabled() {
        return true;
    }
}
