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
            Timestamp expiresAt = Timestamp.ofTimeSecondsAndNanos(Instant.now().plusSeconds(5 * 60).getEpochSecond(),0);

            Map<String, Object> data = new HashMap<>();
            data.put("email", encryptEmail); //암호화된 이메일 그대로
            data.put("used", false);
            data.put("expiresAt", expiresAt);
            data.put("createdAt", Timestamp.now());

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

        } catch(CustomException ce){
            throw ce;
        } catch (Exception e){
            System.out.println(e.getMessage());
            throw new CustomException("일시적 오류가 발생하였습니다.", e.toString(), HttpStatus.INTERNAL_SERVER_ERROR, e);
        }

    }

    public void resetPassword(String token, String newPassword) throws ExecutionException, InterruptedException {
        // 1) 트랜잭션으로 used=false → true 원자 업데이트 (+ 유효성 체크)
        String encEmail = firestore.runTransaction(tx -> {
            DocumentReference ref = firestore.collection("PasswordResetTokens").document(token);
            DocumentSnapshot doc = tx.get(ref).get();

            if (!doc.exists()) {
                throw new CustomException("비밀번호 재설정 시간이 만료되었습니다. 다시 시도해주세요.",
                        "토큰 문서 없음", HttpStatus.BAD_REQUEST, null);
            }

            Boolean used = doc.getBoolean("used");
            Timestamp expiresAt = doc.getTimestamp("expiresAt");
            if (Boolean.TRUE.equals(used)) {
                throw new CustomException("이미 사용된 링크입니다. 다시 요청해주세요.",
                        "used=true", HttpStatus.BAD_REQUEST, null);
            }
            if (expiresAt == null || expiresAt.compareTo(Timestamp.now()) <= 0) {
                throw new CustomException("비밀번호 재설정 시간이 만료되었습니다. 다시 시도해주세요.",
                        "만료", HttpStatus.BAD_REQUEST, null);
            }

            // 여기서 일회용 보장: used=true로 마킹
            tx.update(ref, "used", true, "usedAt", Timestamp.now());

            String email = doc.getString("email"); // 암호화된 이메일
            if (email == null || email.isBlank()) {
                throw new CustomException("비정상 요청입니다. 다시 시도해주세요.",
                        "email 필드 없음", HttpStatus.BAD_REQUEST, null);
            }
            return email;
        }).get();

        // 2) 트랜잭션 커밋 완료 후 실제 비밀번호 변경(서비스 내부에서 decrypt 처리 or 여기서 복호화)
        try {
            userService.updatePasswordByEmail(encEmail, newPassword);
        } catch (Exception e) {
            // (선택) 실패 시 재시도 안내 혹은 신규 토큰 발급 로직
            // 토큰은 이미 used=true 이므로 동일 토큰 재사용은 불가
            throw new CustomException("비밀번호 변경에 실패했습니다.", e.toString(), HttpStatus.INTERNAL_SERVER_ERROR, e);
        }

        // 3) (선택) 토큰 문서 삭제 또는 상태 기입
        firestore.collection("PasswordResetTokens").document(token).delete(); // 선택
    }



}
