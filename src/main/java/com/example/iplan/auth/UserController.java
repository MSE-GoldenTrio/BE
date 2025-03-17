package com.example.iplan.auth;

import com.example.iplan.auth.DTO.SignInDTO;
import com.example.iplan.auth.DTO.SignUpDTO;
import com.example.iplan.config.jwt.JwtToken;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/auth")
public class UserController {
    private final UserService userService;

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

}
