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
import com.example.iplan.util.AES256Encryptor;
import org.apache.commons.codec.digest.DigestUtils;
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
    private final ParentsConsentService parentsConsentService;
    private final JwtTokenProvider jwtTokenProvider;
    private final AES256Encryptor aes;

    @PostMapping("/auth/register")
    @Operation(summary = "회원가입")
    public ResponseEntity<String> signUp(@RequestBody SignUpDTO signUpDto){
        log.info("회원가입 시작: "+signUpDto.getNickname());
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

            String result = userService.signUp(nickname, password, name, verifiedEmail, authority);

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

    @GetMapping("/firebase/custom-token")
    @Operation(summary = "Firebase Custom Token 생성")
    public ResponseEntity<Map<String, String>> generateFirebaseToken(@AuthenticationPrincipal CustomOAuth2UserDetails userDetails) throws FirebaseAuthException {
        String nickname = userDetails.getUsername();
        Users user = userService.findByEncryptedNickname(nickname);

        String customToken = FirebaseAuth.getInstance().createCustomToken(user.getFirebaseAuthUID());
        log.info("Custom Token: {}", customToken);
        return ResponseEntity.ok(Map.of("customToken", customToken));
    }

    @PostMapping("/auth/check-nickname")
    @Operation(summary = "중복 아이디 체크")
    public ResponseEntity<Map<String, Boolean>> checkNickname(@RequestBody Map<String, String> request) {
        String nickname = request.get("nickname");
        log.info("Request nickname = {}", nickname);
        boolean isAvailable = userService.isNicknameAvailable(nickname);
        return ResponseEntity.ok(Map.of("available", isAvailable));
    }

    @PostMapping("/auth/check-email")
    @Operation(summary = "중복 이메일 체크") //여기 해시값으로 수정
    public ResponseEntity<Map<String, Boolean>> checkEmail(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        log.info("Request email = {}", email);
        boolean isAvailable = userService.isEmailAvailable(DigestUtils.sha256Hex(email));
        return ResponseEntity.ok(Map.of("available", isAvailable));
    }

    /**
     * 닉네임으로 사용자 정보 조회 -> 전체 정보 반환!!
     */
    @GetMapping("/unknown/user-info")
    public ResponseEntity<Map<String, Object>> getUserInfo(@AuthenticationPrincipal CustomOAuth2UserDetails userDetails) throws Exception {

        log.info("Checking user info by nickname..");
        String nickname = userDetails.getUsername();
        if (nickname == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }

        log.info("User nickname: {}", nickname);
        Users user = userService.findByEncryptedNickname(nickname);

        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "User not found"));
        }

        // 사용자 정보를 Map으로 변환하여 반환
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("id", user.getId());
        userInfo.put("nickname", aes.decrypt(user.getNickname()));
        userInfo.put("email", aes.decrypt(user.getEmail()));
        userInfo.put("name", aes.decrypt(user.getName()));
        userInfo.put("authority", user.getAuthority().name());
        userInfo.put("linked_id", user.getLinked_id() != null ? user.getLinked_id() : ""); // null 방지
        userInfo.put("uid", user.getFirebaseAuthUID() != null ? user.getFirebaseAuthUID() : "");

        log.info("Received user info: {}", userInfo);
        return ResponseEntity.ok(userInfo);
    }

    /**
     * 소셜 로그인 성공 이후 추가 정보(역할) 업데이트 및 새로운 JWT 발급
     */
    @PostMapping("/unknown/update-role")
    public ResponseEntity<Map<String, String>> updateUserRole(@AuthenticationPrincipal CustomOAuth2UserDetails userDetails, @RequestBody Map<String, String> requestBody) throws Exception {

        String nickname = userDetails.getUsername();

        String roleStr = requestBody.get("role");
        if (roleStr == null || (!roleStr.equals("ROLE_CHILD") && !roleStr.equals("ROLE_PARENT"))) {
            throw new CustomException("사용자 역할이 존재하지 않습니다.", null, HttpStatus.BAD_REQUEST,null );
        }

        // 사용자 역할 업데이트
        userService.updateUserRole(nickname, roleStr);
        log.info("Nickname: {}, Role: {}", nickname, roleStr);

        // SecurityContext 에서 올바른 Authentication(인증객체)을 생성하여 JWT 를 발급해야함
        // 따라서 1) DB 에서 사용자 객체 조회 2)정확한 권한을 포함한 Authentication 객체 생성 후 새로운 토큰을 발급

        // 1. 사용자 객체를 다시 조회 (업데이트된 정보 포함)
        Users updatedUser = userService.findByEncryptedNickname(nickname);
        log.info("Updated user info: {}", updatedUser);

        // 2. 인증객체 생성
        Authentication authentication = getAuthentication(updatedUser);

        // 3. 새로운 JWT 토큰 발급
        JwtToken newToken = jwtTokenProvider.generateToken(authentication);
        log.info("새로운 JWT 토큰 발급 완료 - AccessToken: {}, RefreshToken: {}", newToken.getAccessToken(), newToken.getRefreshToken());

        // 새로운 JWT 토큰 반환
        return ResponseEntity.ok(Map.of(
                "message", "Role updated successfully",
                "accessToken", newToken.getAccessToken(),
                "refreshToken", newToken.getRefreshToken()
        ));
    }

    private static Authentication getAuthentication(Users updatedUser) {
        if (updatedUser == null) {
            throw new CustomException("사용자가 존재하지 않습니다.", null, HttpStatus.NOT_FOUND, null);
        }

        // 추가 정보가 반영된 유저로 CustomOAuth2UserDetails 객체 생성
        CustomOAuth2UserDetails newUserDetails = new CustomOAuth2UserDetails(updatedUser);

        // 이 객체를 이용해 UsernamePasswordAuthenticationToken 기반의 Authentication 객체 생성
        return new UsernamePasswordAuthenticationToken(
                newUserDetails, null, newUserDetails.getAuthorities()
        );
    }

    /**
     * 로그인이 되어있는 사용자가 비밀번호를 변경하려고 할 때
     * @param userDetails
     * @return
     */
    @GetMapping("/change-password")
    public ResponseEntity<?> changePassword(@AuthenticationPrincipal CustomOAuth2UserDetails userDetails) {
        if (userDetails == null) {
            System.out.println("인증 사용자 정보가 존재하지 않습니다.");
            throw new CustomException("인증 정보가 유효하지 않습니다. 다시 로그인 해주세요.", null, HttpStatus.BAD_REQUEST, null);
        }

        try {
            String email = userDetails.getEmail();
            System.out.println("비밀번호 변경 요청 이메일: " + aes.decrypt(email));
            passwordResetService.sendResetLink(email);
            return ResponseEntity.ok(Map.of("message", "이메일 전송에 성공했습니다."));
        } catch (Exception e) {
            System.out.println("예외 발생: " + e.getMessage());
            throw new CustomException("일시적 오류가 발생하였습니다.", e.toString(), HttpStatus.BAD_REQUEST, e);
        }
    }


    /**
     * 비밀번호를 잊은 사용자가 비밀번호를 재설정 하려고 할 때
     * @param payload
     * @return
     */
    @PostMapping("/auth/reset-password-request")
    public ResponseEntity<?> requestResetPassword(@RequestBody Map<String, String> payload) throws Exception {
        String email = payload.get("email");
        Users user = userService.findByHashEmail(email);

        if(user == null) user = userService.findByEncryptedEmail(email);

        System.out.println("Email: " + email);
        System.out.println("User: " + user);

        if(user != null)
        {
            passwordResetService.sendResetLink(email);
            return ResponseEntity.ok(Map.of(
                    "message", "메일 전송에 성공하였습니다."
            ));
        }else{
            throw new CustomException("해당 이메일에 등록된 사용자가 없습니다.", null, HttpStatus.NOT_FOUND, null);
        }
    }

    @PostMapping("/auth/parents/consent/verify")
    public ResponseEntity<?> verifyParentsConsent(@RequestBody Map<String, String> payload) {
        String parentEmail = payload.get("parentEmail");

        log.info("Parent: {}", parentEmail);

        try{
            if(parentEmail != null){
                String token = parentsConsentService.sendParentsConsentRequestMail(parentEmail);
                return ResponseEntity.ok(Map.of(
                        "token", token,
                        "message", "보호자 동의 메일을 성공적으로 보냈습니다."
                ));
            }else{
                throw new CustomException("이메일을 입력해주세요.", null, HttpStatus.BAD_REQUEST, null);
            }
        }catch (Exception e){
            throw new CustomException("일시적 오류가 발생하였습니다.", e.toString(), HttpStatus.INTERNAL_SERVER_ERROR, e);
        }

    }

    @GetMapping("/auth/consent/confirm")
    public ResponseEntity<?> confirmConsent(@RequestParam String token){
        boolean success = parentsConsentService.confirmParentsConsent(token);

        if (!success) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("<html><body><h2 style='text-align:center; margin-top:30vh;'>❌ 유효하지 않은 동의 요청입니다.</h2></body></html>");
        }

        String html = """
        <html>
          <head>
            <meta charset="UTF-8">
            <title>iPlan 동의 완료</title>
          </head>
          <body>
            <h2 style="text-align: center; margin-top: 30vh;">
              ✅ iPlan(계획 달성 어플) 서비스<br/>
              자녀 가입에 동의하셨습니다.
            </h2>
          </body>
        </html>
        """;

        return ResponseEntity.ok().header("Content-Type", "text/html; charset=UTF-8").body(html);
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

        passwordResetService.resetPassword(token, newPassword);

        return ResponseEntity.ok(Map.of(
                "message", "비밀번호 변경에 성공했습니다."
        ));
    }

    @GetMapping("/auth/mailsender-redirect")
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

    @DeleteMapping("/my_page/delete/linked_id")
    public ResponseEntity<?> deleteLinkedID(@AuthenticationPrincipal CustomOAuth2UserDetails userDetails, @RequestParam("linked_id") String encryptedLinkedId){
        log.info("연동을 해제할 linked_id: {}", encryptedLinkedId);

        String encryptedEmail = userDetails.getEmail();
        log.info("연동 해제를 요청한 계정의 email: {}", encryptedEmail);
        if(encryptedEmail != null){
            userService.deleteLinkedId(encryptedEmail, encryptedLinkedId);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "연결된 계정 삭제에 성공하였습니다."
            ));
        }else{
            throw new CustomException("사용자의 이메일을 찾을 수 없습니다.", null, HttpStatus.NOT_FOUND, null);
        }
    }

}
