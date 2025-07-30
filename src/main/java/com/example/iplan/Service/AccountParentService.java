package com.example.iplan.Service;

import com.example.iplan.Domain.PendingAccountRequest;
import com.example.iplan.ExceptionHandler.CustomException;
import com.example.iplan.Repository.AccountRepository;
import com.example.iplan.auth.UserRepository;
import com.example.iplan.auth.UserRole;
import com.example.iplan.auth.Users;
import com.example.iplan.auth.oauth2.CustomOAuth2UserDetails;
import com.example.iplan.auth.jwt.JwtToken;
import com.example.iplan.auth.jwt.JwtTokenProvider;
import com.example.iplan.fcm.FcmRequestDTO;
import com.example.iplan.fcm.FcmRequestService;
import com.example.iplan.fcm.FcmToken;
import com.example.iplan.fcm.FcmTokenService;
import com.example.iplan.util.AES256Encryptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountParentService {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final FcmRequestService fcmRequestService;
    private final FcmTokenService fcmTokenService;
    private final AES256Encryptor aes;

    /**
     * 부모가 자녀 닉네임을 입력하여 연동 요청을 보냄
     */
    public ResponseEntity<Map<String, Object>> sendAccountRequest(String childNickname, String encryptedParentNickname)
            throws ExecutionException, InterruptedException {

        Map<String, Object> response = new HashMap<>();
        String childNicknameHash = DigestUtils.sha256Hex(childNickname);

        // 1. 자녀 유저 조회
        Users childUser = userRepository.findByHashValueNickName(childNicknameHash)
                .orElseThrow(() -> new CustomException("해당 닉네임의 자녀를 찾을 수 없습니다.", HttpStatus.NOT_FOUND));

        // 2. 자녀인지 확인
        if (childUser.getAuthority() != UserRole.CHILD) {
            throw new CustomException("입력한 닉네임은 자녀 계정이 아닙니다.", HttpStatus.BAD_REQUEST);
        }

        // 3. 이미 다른 부모와 연동된 자녀인지 확인
        log.info("이미 연동된 자녀인지 체크..");
        List<String> linkedIds = childUser.getLinked_id();
        if (linkedIds != null && linkedIds.stream().anyMatch(Objects::nonNull)) {
            response.put("success", false);
            response.put("message", "해당 자녀 계정은 이미 다른 계정과 연동되어 있습니다.");
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }

        log.info("수락되지 않은 동일한 요청 체크..");
        // 4. 수락되지 않은 동일한 요청이 이미 존재하는지 확인
        PendingAccountRequest existingRequest = accountRepository.findExistingRequest(childNicknameHash, encryptedParentNickname);
        if (existingRequest != null) {
            response.put("success", false);
            response.put("message", "이미 해당 자녀에게 연동 요청을 보냈습니다. 요청이 승인될 때까지 기다려주세요.");
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }

        log.info("이미 해당 계정과 연동이 되어있는지 체크..");
        // 5. 이미 해당 계정과 연동이 되어있는지 확인
        PendingAccountRequest approvedRequest = accountRepository.findApprovedRequest(childNicknameHash, encryptedParentNickname);
        if (approvedRequest != null) {
            response.put("success", false);
            response.put("message", "이미 해당 자녀와 연동이 되어있습니다.");
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }

        // 5. PendingAccountRequest 생성
        PendingAccountRequest request = PendingAccountRequest.builder()
                .childHashedNickname(childNicknameHash)
                .parentEncryptedNickname(encryptedParentNickname)
                .approved(false)
                .status("pending")
                .build();

        // 6. 저장
        accountRepository.saveWithAutoIncrement(request);
        log.info("PendingAccountRequest 저장 완료: {}", request.getId());

        // 7. 연동 요청을 보낼 아이의 FcmToken 찾기
        List<FcmToken> fcmTokens = fcmTokenService.getTokensByHashedUserId(childUser.getNicknameHash()); //아이디 해시값으로 찾기

        log.info("encryptedParentNickname = {}", encryptedParentNickname);
        try {
            if(!fcmTokens.isEmpty()){
                // 8. 아이의 존재하는 모든 FcmToken 으로 푸시알림 전송
                for(FcmToken fcmToken : fcmTokens){
                    log.info("아이의 fcmToken: {}", fcmToken.getToken());
                    FcmRequestDTO requestDTO = FcmRequestDTO.builder()
                            .user_id(fcmToken.getUser_id()) // 해시된 user_id
                            .fcmToken(fcmToken.getToken()) // 그냥 아무것도 안된 token
                            .notification(FcmRequestDTO.Notification.builder()
                                    .title("iPlan")
                                    .body(aes.decrypt(encryptedParentNickname) + " 부모님이 연동 요청을 보냈습니다. 눌러서 확인하세요.") // 평문 id
                                    .build())
                            .data(FcmRequestDTO.Data.builder()
                                    .pendingRequestId(request.getId())
                                    .sender(aes.decrypt(encryptedParentNickname)) // 평문 id
                                    .type("AccountLinkRequest")
                                    .build())
                            .build();
                    fcmRequestService.sendPush(requestDTO);
                    log.info("연동 요청 푸시알림을 성공적으로 보냈습니다.");
                }
            } else {
                log.warn("연동 요청을 보낼 아이({})의 FcmToken 이 존재하지 않습니다.", childUser.getNickname());
            }
        } catch (Exception e) {
            throw new CustomException(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }

        // 9. 응답 반환
        response.put("success", true);
        response.put("message", "자녀에게 연동 요청이 전송되었습니다.");
        return new ResponseEntity<>(response, HttpStatus.OK);
    }


    /**
     * 부모가 이미 보낸 연동요청이 있는지 체크
     */
    public ResponseEntity<Map<String, Object>> getParentPendingStatus(String encryptedParentNickname) {
        try {
            PendingAccountRequest pendingRequests = accountRepository.findParentRequest(encryptedParentNickname);

            if (pendingRequests != null) {
                String childHashedNickname = pendingRequests.getChildHashedNickname();
                log.info("요청된 child 해시 닉네임: {}", childHashedNickname);

                // Users 테이블에서 childHashedNickname 일치하는 사용자 찾기
                Users childUser = userRepository.findByHashValueNickName(childHashedNickname)
                        .orElseThrow(() -> new CustomException("사용자를 찾을 수 없습니다", HttpStatus.NOT_FOUND));
                if (childUser == null) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                            "success", false,
                            "message", "해당 자녀 사용자를 찾을 수 없습니다."
                    ));
                }

                // 복호화된 닉네임 얻기
                String decryptedNickname = aes.decrypt(childUser.getNickname());
                log.info("복호화된 자녀 닉네임: {}", decryptedNickname);

                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "childNickname", decryptedNickname
                ));
            } else {
                return ResponseEntity.ok(Map.of("status", "none"));
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }


    /**
     * 연동 요청이 거부된 것은 부모가 확인 후에 엔티티에서 삭제
     */
    public ResponseEntity<Map<String, Object>> deleteDeniedRequest(String encryptedParentNickname) {
        try {
            PendingAccountRequest deniedRequest = accountRepository.findByFields(
                    Map.of(
                            "parentEncryptedNickname", encryptedParentNickname,
                            "approved", false,
                            "status", "denied"
                    )
            );

            if (deniedRequest != null) {
                accountRepository.delete(deniedRequest);
                log.info("Denied 상태의 요청을 삭제했습니다: {}", deniedRequest.getId());
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "거절된 연동 요청이 삭제되었습니다."
                ));
            } else {
                return ResponseEntity.ok(Map.of(
                        "success", false,
                        "message", "삭제할 거절된 요청이 없습니다."
                ));
            }
        } catch (Exception e) {
            log.error("Denied 요청 삭제 중 오류 발생", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "거절된 요청 삭제 중 오류가 발생했습니다.",
                    "error", e.getMessage()
            ));
        }
    }

}
