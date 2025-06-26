package com.example.iplan.auth;

import com.example.iplan.auth.jwt.JwtToken;
import com.example.iplan.auth.jwt.JwtTokenProvider;
import com.example.iplan.auth.oauth2.CustomOAuth2UserDetails;
import com.example.iplan.auth.redis.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {
    private final UserRepository userRepository;
    private final AuthenticationManagerBuilder authenticationManagerBuilder;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;

    // 회원가입
    public String signUp(String nickname, String password, String name, String email, String roleStr) {
        try {
            // 1. 아이디 중복 확인
            if (nickname != null && userRepository.findByNickname(nickname).isPresent()) {
                throw new IllegalArgumentException("Nickname already exists.");
            }

            UserRole role = UserRole.fromString(roleStr);   // Enum 변환

            // 2. Users 객체 생성
            Users user = Users.builder()
                    .nickname(nickname)
                    .email(email)
                    .password(passwordEncoder.encode(password)) // 비밀번호 암호화
                    .name(name)
                    .authority(role)    // child, parent
                    .linked_id(new ArrayList<>()) // 빈 리스트로 초기화
                    .build();

            // 3. 사용자 정보 User 컬렉션에 저장 -> 자동 증가된 ID로 저장
            userRepository.saveWithAutoIncrement(user);

            return "Sign Up Successfully";
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException("Error accessing Firestore", e);
        }
    }

    // 로그인
    public JwtToken signIn(String nickname, String password, String fcmToken) {
        try {
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

            // 5. 인증 객체 (Authentication)을 바탕으로 JWT 토큰 생성
            JwtToken jwtToken = jwtTokenProvider.generateToken(authentication);
            log.info("JwtToken created: accessToken = {}, refreshToken = {}", jwtToken.getAccessToken(), jwtToken.getRefreshToken());

            // 6. Refresh 토큰 Redis 에 저장
            long expirationMinutes = JwtTokenProvider.REFRESH_TOKEN_EXPIRATION / 1000 / 60; // ms → minutes
            refreshTokenService.saveToken(
                    (CustomOAuth2UserDetails) authentication.getPrincipal(),
                    jwtToken.getRefreshToken(),
                    expirationMinutes
            );
            log.info("Saved refresh token in Redis: nickname={}, ttl={}min", nickname, expirationMinutes);

            // 7. jwt 반환
            return jwtToken;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다.");
        }
    }

    /**
     * 닉네임을 기반으로 사용자 조회
     */
    public Users findByNickname(String nickname) {
        Optional<Users> user = userRepository.findByNickname(nickname);
        return user.orElse(null); // 사용자 없을 경우 null 반환
    }

    public Users findByEmail(String email){
        Optional<Users> user = userRepository.findByEmail(email);
        return user.orElse(null);
    }

    /**
     * 아이디(닉네임 중복 체크
     */
    public boolean isNicknameAvailable(String nickname) {
        Optional<Users> user = userRepository.findByNickname(nickname);
        return user.isEmpty(); // 사용 가능하면 true, 중복이면 false
    }

    /**
     * 소셜 로그인 성공 이후 추가 정보(역할) 업데이트
     */
    public void updateUserRole(String nickname, String roleStr) {
        try {
            // 닉네임으로 사용자 조회
            Users user = userRepository.findByNickname(nickname)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found"));

            // 문자열을 UserRole Enum으로 변환
            UserRole role = UserRole.fromString(roleStr);
            user.setAuthority(role); // ✅ 역할 업데이트

            userRepository.update(user);
            log.info("Updated successfully: {}, {}", nickname, role);
        } catch (ExecutionException e) {
            log.error("Firestore ExecutionException Error.. {}", e.getMessage());
            throw new RuntimeException("Firestore 데이터 처리 중 오류 발생", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Firestore InterruptedException Error.. {}", e.getMessage());
            throw new RuntimeException("Firestore 작업이 중단되었습니다.", e);
        } catch (Exception e) {
            log.error("Error..{}", e.getMessage());
            throw new RuntimeException("사용자 역할 업데이트 처리 중 오류 발생", e);
        }
    }
}

