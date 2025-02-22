package com.example.iplan.auth;

import com.example.iplan.config.jwt.JwtToken;
import com.example.iplan.config.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutionException;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {
    private final UserRepository userRepository;
    private final AuthenticationManagerBuilder authenticationManagerBuilder;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    // 회원가입
    public String signUp(String nickname, String password, String email, String name, String role) {
        try {
            // 1. 아이디 중복 확인
            if (nickname != null && userRepository.findByNickname(nickname).isPresent()) {
                throw new IllegalArgumentException("Nickname already exists.");
            }

            // 2. 이메일 중복 확인
            if (email != null && userRepository.findByEmail(email).isPresent()) {
                throw new IllegalArgumentException("User email already exists.");
            }

            // 2. Users 객체 생성
            Users user = Users.builder()
                    .nickname(nickname)
                    .email(email)   // 기본값 null
                    .password(passwordEncoder.encode(password)) // 비밀번호 암호화
                    .name(name)
                    .authority(role)    // child, parent
                    .build();

            // 3. 사용자 정보 User 컬렉션에 저장
            userRepository.save(user);
            return "Sign Up Successfully";
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException("Error accessing Firestore", e);
        }
    }

    // 로그인
    public JwtToken signIn(String nickname, String password, String fcmToken) {
        // 1. 사용자의 입력값으로 UsernamePasswordAuthenticationToken 생성 -> 비밀번호 검증을 위해 사용됨
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(nickname, password);
        log.info("Passed signIn 1");

        // AuthenticationManager 가 로그인 요청을 처리 (여기서 사용자 인증과 비밀번호 검증이 이루어짐)
        // 내부적으로 BCryptPasswordEncoder.matches(입력된 비밀번호, 저장된 암호화된 비밀번호)를 실행하여 검증

        // 2. AuthenticationManager.authenticate()가 호출됨
        // 2-1. 여기서 AuthenticationManager 가 CustomUserDetailsService.loadUserByUsername()을 내부적으로 호출
        // -> 디비에서 해당 이메일을 가진 사용자 조회 후 CustomUserDetails 객체 반환
        // 2-2. 이후 AuthenticationManager 가 CustomUserDetails 객체와 위에서 생성한 UsernamePasswordAuthenticationToken 울 바교하여 사용자 인증을 알아서 해줌
        // 2-3. 검증 완료되면 CustomUserDetails 객체를 인증 객체 (Authentication)로 변환하여 인증 완료
        Authentication authentication = authenticationManagerBuilder.getObject().authenticate(authenticationToken);
        log.info("Passed signIn 2");

        // 3. 사용자 인증 이후 Authentication 객체를 SecurityContextHolder 에 저장
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // 인증된 사용자 조회
        Users user = userRepository.findByNickname(nickname)
                .orElseThrow(() -> new IllegalArgumentException("User not found."));

        // 4. fcmToken 디비에 업데이트
        user.setFcmToken(fcmToken);
        log.info("Updated fcmToken for user: {}", nickname);

        // 인증 객체 (Authentication)을 바탕으로 JWT 토큰 생성
        JwtToken jwtToken = jwtTokenProvider.generateToken(authentication);
        log.info("JwtToken created: accessToken = {}, refreshToken = {}", jwtToken.getAccessToken(), jwtToken.getRefreshToken());

        return jwtToken;
    }

    // 특정 사용자 조회
    public Users getUserById(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + userId));
    }
}

