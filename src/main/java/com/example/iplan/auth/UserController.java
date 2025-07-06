package com.example.iplan.auth;

import com.example.iplan.ExceptionHandler.CustomException;
import com.example.iplan.auth.DTO.SignInDTO;
import com.example.iplan.auth.DTO.SignUpDTO;
import com.example.iplan.auth.oauth2.CustomOAuth2UserDetails;
import com.example.iplan.auth.jwt.JwtToken;
import com.example.iplan.auth.jwt.JwtTokenProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api")
public class UserController {
    private final UserService userService;
    private final PasswordResetService passwordResetService;
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping("/auth/register")
    @Operation(summary = "회원가입")
    public ResponseEntity<String> signUp(@RequestBody SignUpDTO signUpDto){
        try {
            String idToken = signUpDto.getIdToken();
            log.info("Firebase 본인인증 토큰: {}", idToken);

            // firebase ID 토큰 검증
            FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(idToken);

            if (!decodedToken.isEmailVerified()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("이메일 본인인증이 완료되지 않았습니다.");
            }

            // Firebase 토큰에서 검증된 이메일 추출 (기존에는 SignUpDTO email)
            String verifiedEmail = decodedToken.getEmail();

            String nickname = signUpDto.getNickname();
            String password = signUpDto.getPassword();
            String name = signUpDto.getName();
            String authority = signUpDto.getAuthority();
            log.info("Register request: nickname = {}, password = {}, name = {}, email = {}, authority: {}", nickname, password, name, verifiedEmail, authority);

            String result = userService.signUp(nickname, password, name, verifiedEmail, authority);
            log.info("Register result: {}", result);

            return ResponseEntity.ok(result);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (FirebaseAuthException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("유효하지 않은 Firebase 토큰입니다.");
        }
    }

    @PostMapping("/auth/login")
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


    @PostMapping("/auth/check-nickname")
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
    @GetMapping("/unknown/user-info")
    public ResponseEntity<Map<String, Object>> getUserInfo(@AuthenticationPrincipal CustomOAuth2UserDetails userDetails) {

        log.info("Checking user info by nickname..");
        String nickname = userDetails.getUsername();
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
    @PostMapping("/unknown/update-role")
    public ResponseEntity<Map<String, String>> updateUserRole(@AuthenticationPrincipal CustomOAuth2UserDetails userDetails, @RequestBody Map<String, String> requestBody) {

        log.info("Update user role by nickname..");
        String nickname = userDetails.getUsername();
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
        CustomOAuth2UserDetails newUserDetails = new CustomOAuth2UserDetails(updatedUser);

        // 이 객체를 이용해 UsernamePasswordAuthenticationToken 기반의 Authentication 객체 생성
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                newUserDetails, null, newUserDetails.getAuthorities()
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

    /**
     * 로그인이 되어있는 사용자가 비밀번호를 변경하려고 할 때
     * @param userDetails
     * @return
     */
    @GetMapping("/change-password")
    public ResponseEntity<?> changePassword(@AuthenticationPrincipal CustomOAuth2UserDetails userDetails) {
        System.out.println("컨트롤러 진입");
        if (userDetails == null) {
            System.out.println("인증 사용자 정보가 존재하지 않습니다.");
            throw new CustomException("인증 정보가 유효하지 않습니다. 다시 로그인 해주세요.", HttpStatus.BAD_REQUEST);
        }

        try {
            String email = userDetails.getEmail();
            System.out.println("비밀번호 변경 요청 이메일: " + email);
            passwordResetService.sendResetLink(email);
            return ResponseEntity.ok(Map.of("message", "이메일 전송에 성공했습니다."));
        } catch (Exception e) {
            System.out.println("예외 발생: " + e.getMessage());
            throw new CustomException("서버 오류가 발생했습니다: " + e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }


    /**
     * 비밀번호를 잊은 사용자가 비밀번호를 재설정 하려고 할 때
     * @param payload
     * @return
     */
    @PostMapping("/auth/reset-password-request")
    public ResponseEntity<?> requestResetPassword(@RequestBody Map<String, String> payload) throws ExecutionException, InterruptedException {
        String email = payload.get("email");
        Users user = userService.findByEmail(email);
        System.out.println("Email: " + email);
        System.out.println("User: " + user);

        if(user != null)
        {
            passwordResetService.sendResetLink(email);
            return ResponseEntity.ok(Map.of(
                    "message", "메일 전송에 성공하였습니다."
            ));
        }else{
            throw new CustomException("해당 이메일에 등록된 사용자가 없습니다.", HttpStatus.NOT_FOUND);
        }
    }

    /**
     * 새로운 비밀번호로 재설정
     * @param payload
     * @return
     * @throws ExecutionException
     * @throws InterruptedException
     */
    @PostMapping("/auth/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> payload) throws ExecutionException, InterruptedException {
        String token = payload.get("token");
        String newPassword = payload.get("newPassword");

        System.out.println("Token: " + token + ", New Password: " + newPassword);

        passwordResetService.resetPassword(token, newPassword);

        return ResponseEntity.ok(Map.of(
                "message", "비밀번호 변경에 성공했습니다."
        ));
    }

    @GetMapping("/auth/reset-password-redirect")
    public ResponseEntity<String> redirectPage(@RequestParam String token) {
        String html = """
        <html>
        <head>
          <meta charset="UTF-8">
          <title>앱으로 이동 중...</title>
          <script>
            const token = '%s';
            const scheme = `iplan://reset-password?token=${token}`;
            alert("Redirecting to: " + scheme);
            window.onload = () => {
              setTimeout(() => {
                window.location.href = scheme;
              }, 1000);
            };
          </script>
        </head>
        <body>
          <p>앱으로 이동 중입니다...</p>
          <p>앱이 열리지 않으면 <a href="iplan://reset-password?token=%s">여기를 클릭하세요</a></p>
        </body>
        </html>
        """.formatted(token, token);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "html", StandardCharsets.UTF_8));
        return new ResponseEntity<>(html, headers, HttpStatus.OK);
    }

}
