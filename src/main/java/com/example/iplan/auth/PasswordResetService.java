package com.example.iplan.auth;

import com.example.iplan.ExceptionHandler.CustomException;
import com.example.iplan.util.AES256Encryptor;
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
    private final AES256Encryptor aes;

    /**
     * 비밀번호 재설정 링크를 유저 이메일에 전송하기 위한 메서드
     * @param encryptEmail
     */
    public void sendResetLink(String encryptEmail){
        try{
            String token = UUID.randomUUID().toString();
            Timestamp expiresAt = Timestamp.ofTimeSecondsAndNanos(Instant.now().plusSeconds(1800).getEpochSecond(),0);

            Map<String, Object> data = new HashMap<>();
            data.put("email", encryptEmail); //암호화된 이메일 그대로
            data.put("expiresAt", expiresAt);

            firestore.collection("PasswordResetTokens").document(token).set(data).get();

            //테스트 시 본인 로컬 IP주소로 변경(추후 서버 주소로 변경)
            String webRedirectLink = "https://iplanner.site/api/auth/mailsender-redirect?token=" + token;
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            String html = "<p>아래 버튼을 클릭하면 앱이 실행됩니다:</p>" +
                    "<a href=\"" + webRedirectLink + "\">비밀번호 재설정</a>";

            helper.setTo(aes.decrypt(encryptEmail));
            helper.setSubject("[iPlan] 비밀번호 재설정 링크입니다.");
            helper.setText(html, true);

            mailSender.send(mimeMessage);

        }catch (Exception e){
            System.out.println(e.getMessage());
            throw new CustomException("일시적 오류가 발생하였습니다.", e.toString(), HttpStatus.BAD_REQUEST, e);
        }

    }

    public void resetPassword(String token, String newPassword) throws ExecutionException, InterruptedException {
        DocumentReference docRef = firestore.collection("PasswordResetTokens").document(token);
        DocumentSnapshot doc = docRef.get().get();

        if(!doc.exists()) {
            System.out.println("유효하지 않는 토큰입니다.");
            throw new CustomException("비밀번호 재설정 시간이 만료되었습니다. 다시 시도해주세요.", "비밀번호 재설정 토큰이 존재하지 않습니다.", HttpStatus.BAD_REQUEST, null);
        }

        System.out.println(doc);

        Timestamp expiresAt = doc.getTimestamp("expiresAt");
        assert expiresAt != null;
        if(expiresAt.toDate().before(new java.util.Date())){
            System.out.println("토큰이 만료되었습니다.");
            throw new CustomException("비밀번호 재설정 시간이 만료되었습니다. 다시 시도해주세요.", null, HttpStatus.BAD_REQUEST, null);
        }

        String email = doc.getString("email"); // 암호화된 이메일 그대로 가져오기
        userService.updatePasswordByEmail(email, newPassword);
        docRef.delete().get();
    }


}
