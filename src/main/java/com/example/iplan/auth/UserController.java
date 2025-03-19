package com.example.iplan.auth;

import com.example.iplan.auth.DTO.SignInDTO;
import com.example.iplan.auth.DTO.SignUpDTO;
import com.example.iplan.auth.oauth2.CustomOAuth2UserDetails;
import com.example.iplan.config.jwt.JwtToken;
import com.example.iplan.config.jwt.JwtTokenProvider;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/auth")
public class UserController {
    private final UserService userService;
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping("/register")
    @Operation(summary = "회원가입")
    public ResponseEntity<String> signUp(@RequestBody SignUpDTO signUpDto){
        try {
            String nickname = signUpDto.getNickname();
            String password = signUpDto.getPassword();
            String name = signUpDto.getName();
            String email = signUpDto.getEmail();
            String authority = signUpDto.getAuthority();
            log.info("Register request: nickname = {}, password = {}, name = {}, email = {}, authority: {}", nickname, password, name, email, authority);

            String result = userService.signUp(nickname, password, name, email, authority);
            log.info("Register result: {}", result);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/login")
    @Operation(summary = "로그인")
    public JwtToken signIn(@RequestBody SignInDTO signInDto) {
        String nickname = signInDto.getNickname();
        String password = signInDto.getPassword();
        String fcmToken = signInDto.getFcmToken();
        log.info("Login request: nickname = {}, password = {}, fcmToken = {}", nickname, password, fcmToken);

        JwtToken jwtToken = userService.signIn(nickname, password, fcmToken);
        log.info("JwtToken accessToken = {}, refreshToken = {}", jwtToken.getAccessToken(), jwtToken.getRefreshToken());
        return jwtToken;
    }


    @PostMapping("/check-nickname")
    @Operation(summary = "중복 아이디 체크")
    public ResponseEntity<Map<String, Boolean>> checkNickname(@RequestBody Map<String, String> request) {
        String nickname = request.get("nickname");
        log.info("Request nickname = {}", nickname);
        boolean isAvailable = userService.isNicknameAvailable(nickname);
        return ResponseEntity.ok(Map.of("available", isAvailable));
    }

    /**
     * 닉네임으로 사용자 정보 조회 -> 전체 정보 반환!!
     */
    @GetMapping("/user-info")
    public ResponseEntity<Map<String, Object>> getUserInfo(@AuthenticationPrincipal String nickname) {
        log.info("Checking user info by nickname..");
        if (nickname == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }

        log.info("User nickname: {}", nickname);
        Users user = userService.findByNickname(nickname);

        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "User not found"));
        }

        // 사용자 정보를 Map으로 변환하여 반환
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("id", user.getId());
        userInfo.put("nickname", user.getNickname());
        userInfo.put("email", user.getEmail());
        userInfo.put("name", user.getName());
        userInfo.put("authority", user.getAuthority().name());
        userInfo.put("linked_id", user.getLinked_id() != null ? user.getLinked_id() : ""); // null 방지
        userInfo.put("fcmToken", user.getFcmToken() != null ? user.getFcmToken() : ""); // null 방지

        log.info("Received user info: {}", userInfo);
        return ResponseEntity.ok(userInfo);
    }

    /**
     * 소셜 로그인 성공 이후 추가 정보(역할) 업데이트 및 새로운 JWT 발급
     */
    @PostMapping("/update-role")
    public ResponseEntity<Map<String, String>> updateUserRole(@AuthenticationPrincipal String nickname, @RequestBody Map<String, String> requestBody) {
        log.info("Update user role by nickname..");
        if (nickname == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }

        String roleStr = requestBody.get("role"); // 요청에서 역할 가져오기
        if (roleStr == null || (!roleStr.equals("ROLE_CHILD") && !roleStr.equals("ROLE_PARENT"))) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid role"));
        }

        userService.updateUserRole(nickname, roleStr); // 사용자 역할 업데이트
        log.info("Nickname: {}, Role: {}", nickname, roleStr);

        // SecurityContext에서 올바른 Authentication을 생성하여 JWT를 발급해야함
        // 따라서 1. DB에서 사용자 객체 조회 2. 정확한 권한을 포함한 Authentication 객체 생성 후 새로운 토큰을 발급

        // 1. 사용자 객체를 다시 조회 (업데이트된 역할 포함)
        Users updatedUser = userService.findByNickname(nickname);
        log.info("Updated user info: {}", updatedUser);
        if (updatedUser == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "User not found"));
        }

        // 2. CustomOAuth2UserDetails 객체를 생성
        CustomOAuth2UserDetails userDetails = new CustomOAuth2UserDetails(updatedUser);

        // 이 객체를 이용해 UsernamePasswordAuthenticationToken 기반의 Authentication 객체 생성
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities()
        );

        // 3. 새로운 JWT 토큰 발급
        JwtToken newToken = jwtTokenProvider.generateToken(authentication);
        log.info("새로운 JWT 토큰 발급 완료 - AccessToken: {}", newToken.getAccessToken());

        // 새로운 JWT 토큰 반환
        return ResponseEntity.ok(Map.of(
                "message", "Role updated successfully",
                "accessToken", newToken.getAccessToken()
        ));
    }
}
