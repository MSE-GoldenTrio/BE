package com.example.iplan.auth;

import com.example.iplan.ExceptionHandler.CustomException;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.mail.javamail.MimeMessageHelper;
import jakarta.mail.internet.MimeMessage;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetService {
    private final UserService userService;
    private final Firestore firestore;
    private final JavaMailSender mailSender;

    /**
     * 비밀번호 재설정 링크를 유저 이메일에 전송하기 위한 메서드
     * @param email
     */
    public void sendResetLink(String email){
        if(userService.findByEmail(email) == null){
            throw new CustomException("해당 이메일의 유저를 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
        }

        try{
            String token = UUID.randomUUID().toString();
            Timestamp expiresAt = Timestamp.ofTimeSecondsAndNanos(Instant.now().plusSeconds(1800).getEpochSecond(),0);

            Map<String, Object> data = new HashMap<>();
            data.put("email", email);
            data.put("expiresAt", expiresAt);

            firestore.collection("PasswordResetTokens").document(token).set(data).get();

            //테스트 시 본인 로컬 IP주소로 변경(추후 서버 주소로 변경)
            String webRedirectLink = "http://192.168.123.104:8080/api/auth/reset-password-redirect?token=" + token;
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            String html = "<p>아래 버튼을 클릭하면 앱이 실행됩니다:</p>" +
                    "<a href=\"" + webRedirectLink + "\">비밀번호 재설정</a>";

            helper.setTo(email);
            helper.setSubject("[iPlan] 비밀번호 재설정 링크입니다.");
            helper.setText(html, true);

            mailSender.send(mimeMessage);

        }catch (Exception e){
            System.out.println(e.getMessage());
            throw new CustomException(e.getMessage(), HttpStatus.BAD_REQUEST);
        }

    }

    public void resetPassword(String token, String newPassword) throws ExecutionException, InterruptedException {
        DocumentReference docRef = firestore.collection("PasswordResetTokens").document(token);
        DocumentSnapshot doc = docRef.get().get();

        if(!doc.exists()) {
            System.out.println("유효하지 않는 토큰입니다.");
            throw new CustomException("유효하지 않는 토큰입니다.", HttpStatus.BAD_REQUEST);
        }

        System.out.println(doc);

        Timestamp expiresAt = doc.getTimestamp("expiresAt");
        if(expiresAt.toDate().before(new java.util.Date())){
            System.out.println("토큰이 만료되었습니다.");
            throw new CustomException("토큰이 만료되었습니다.", HttpStatus.REQUEST_TIMEOUT);
        }

        String email = doc.getString("email");
        userService.updatePasswordByEmail(email, newPassword);
        docRef.delete().get();
    }


}
