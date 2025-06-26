package com.example.iplan.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import com.example.iplan.auth.oauth2.CustomOAuth2UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * 일반 로그인 & OAuth2 로그인 통합 관리 가능하도록 변경 !!
     * 기존 CustomUserDetails 대신 CustomOAuth2UserDetails 사용
     */
    // UserService 의 로그인 과정에서 호출 됨 -> 디비에서 해당 이메일을 가진 사용자 조회
    @Override
    public UserDetails loadUserByUsername(String nickname) throws UsernameNotFoundException {
        Users user = userRepository.findByNickname(nickname)
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));

        log.info("Loaded user from DB: {}, {}", user.getNickname(), user.getEmail());

        return new CustomOAuth2UserDetails(user);
    }

    /**
     * CustomUserDetails.builder()를 사용하여 직접 객체 생성
     * 일반 로그인만 지원 -> CustomUserDetails 사용
     */
    // 해당하는 User 의 데이터가 존재한다면 CustomUserDetails 객체로 만들어서 return
//    private UserDetails createUserDetails(Users users) {
//        return CustomUserDetails.builder()
//                .username(users.getEmail())
//                .password(users.getPassword())  // 디비에서 암호화된 비밀번호 가져옴
//                .role(UserRole.fromString(users.getAuthority()))  // 사용자 권한 설정 -> 문자열(child, parent)을 Enum 으로 변환
//                .build();
//    }

}

