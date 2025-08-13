package com.example.iplan.auth;

import com.example.iplan.ExceptionHandler.CustomException;
import com.example.iplan.util.AES256Encryptor;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class ParentsConsentService {
    private final JavaMailSender mailSender;
    private final Firestore firestore;
    private final AES256Encryptor aes;

    public String sendParentsConsentRequestMail(String parentEmail){
        try{
            String token = UUID.randomUUID().toString();
            String encryptParentEmail = aes.encrypt(parentEmail);
            Timestamp expiresAt = Timestamp.ofTimeSecondsAndNanos(Instant.now().plusSeconds(180).getEpochSecond(),0); //3분으로 줄임

            Map<String, Object> data = new HashMap<>();
            data.put("parentEmail", encryptParentEmail);
            data.put("parentHashEmail", DigestUtils.sha256Hex(parentEmail));
            data.put("expiresAt", expiresAt);
            data.put("consent", false);

            firestore.collection("ParentsConsentTokens").document(token).set(data).get();

            String webRedirectLink = "https://iplanner.site/api/auth/consent/confirm?token=" + token;
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            String html = "<p>안녕하세요. 귀하의 자녀가 iPlan(계획 달성 어플) 서비스를 이용하기 위해 가입을 시도하였습니다.\n" +
                    "아래 버튼을 눌러 자녀의 가입에 동의해 주세요:</p>" +
                    "<a href=\"" + webRedirectLink + "\">[동의합니다]</a>";
            helper.setTo(parentEmail);
            helper.setSubject("[iPlan] 자녀의 가입을 위한 보호자 동의 요청");
            helper.setText(html, true);

            mailSender.send(mimeMessage);

            return token;

        } catch (Exception e) {
            throw new CustomException("일시적 오류가 발생하였습니다.", e.toString(), HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
    }

    public boolean confirmParentsConsent(String token) {
        DocumentReference docRef = firestore.collection("ParentsConsentTokens").document(token);

        try{
            DocumentSnapshot snapshot = docRef.get().get();
            if(!snapshot.exists()){
                throw new CustomException("보호자 동의 시간이 만료되었습니다. 다시 시도해주세요", "보호자 동의 토큰이 유효하지 않습니다.", HttpStatus.BAD_REQUEST, null);
            }
            Map<String, Object> updates = new HashMap<>();
            updates.put("consent", true);
            updates.put("confirmedAt", Timestamp.now());

            docRef.update(updates);

            return true;
        }catch (Exception e){
            throw new CustomException("일시적 오류가 발생하였습니다.", e.toString(), HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
    }
}
