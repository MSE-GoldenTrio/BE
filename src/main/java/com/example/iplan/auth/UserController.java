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
            String result = userService.signUp(signUpDto.getEmail(), signUpDto.getPassword(), signUpDto.getName(), signUpDto.getAuthority());
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/login")
    @Operation(summary = "로그인")
    public JwtToken signIn(@RequestBody SignInDTO signInDto) {
        String email = signInDto.getEmail();
        String password = signInDto.getPassword();
        String fcmToken = signInDto.getFcmToken();
        log.info("Login request: email = {}, password = {}, fcmToken = {}", email, password, fcmToken);

        JwtToken jwtToken = userService.signIn(email, password, fcmToken);
        log.info("JwtToken accessToken = {}, refreshToken = {}", jwtToken.getAccessToken(), jwtToken.getRefreshToken());
        return jwtToken;
    }
}
