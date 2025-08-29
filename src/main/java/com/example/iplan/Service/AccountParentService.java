package com.example.iplan.Service;

import com.example.iplan.Domain.PendingAccountRequest;
import com.example.iplan.ExceptionHandler.CustomException;
import com.example.iplan.Repository.AccountRepository;
import com.example.iplan.auth.UserRepository;
import com.example.iplan.auth.UserRole;
import com.example.iplan.auth.Users;
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
    public ResponseEntity<Map<String, Object>> sendAccountRequest(String childNickname, String parentEncryptedNickname)
            throws ExecutionException, InterruptedException {

        Map<String, Object> response = new HashMap<>();
        String childNicknameHash = DigestUtils.sha256Hex(childNickname);    // DTO 평문 닉네임 -> 해시값으로 바꿔서 조회

        // 1. 자녀 유저 조회
        Users childUser = userRepository.findByHashValueNickName(childNicknameHash)
                .orElseThrow(() -> new CustomException("해당 닉네임의 자녀를 찾을 수 없습니다.", null, HttpStatus.NOT_FOUND, null));
        String childEncryptedNickname = childUser.getNickname();    // 아이의 암호화된 닉네임

        // 2. 자녀인지 확인
        if (childUser.getAuthority() != UserRole.CHILD) {
            throw new CustomException("입력한 닉네임은 자녀 계정이 아닙니다.", null, HttpStatus.BAD_REQUEST, null);
        }

        // 3. 이미 다른 부모와 연동된 자녀인지 확인
        log.info("이미 연동된 자녀인지 체크..");
        List<String> linkedIds = childUser.getLinked_id();
        if (linkedIds != null && linkedIds.stream().anyMatch(Objects::nonNull)) {
            response.put("success", false);
            response.put("message", "해당 자녀 계정은 이미 다른 계정과 연동되어 있습니다.");
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }

        // 4. 수락되지 않은 동일한 요청이 이미 존재하는지 확인
        log.info("수락되지 않은 동일한 요청 체크..");
        PendingAccountRequest existingRequest = accountRepository.findExistingRequest(childEncryptedNickname, parentEncryptedNickname, false, "pending");
        if (existingRequest != null) {
            response.put("success", false);
            response.put("message", "이미 해당 자녀에게 연동 요청을 보냈습니다. 요청이 승인될 때까지 기다려주세요.");
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }

        // 5. PendingAccountRequest 생성
        PendingAccountRequest request = PendingAccountRequest.builder()
                .childEncryptedNickname(childEncryptedNickname)
                .parentEncryptedNickname(parentEncryptedNickname)
                .approved(false)
                .status("pending")
                .build();

        // 6. 저장
        accountRepository.saveWithAutoIncrement(request);
        log.info("PendingAccountRequest 저장 완료: {}", request.getId());

        // 7. 연동 요청을 보낼 아이의 FcmToken 찾기
        List<FcmToken> fcmTokens = fcmTokenService.getTokensByHashedUserId(childUser.getNicknameHash()); //아이디 해시값으로 찾기

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
                                    .body(aes.decrypt(parentEncryptedNickname) + " 부모님이 연동 요청을 보냈어요! 눌러서 확인하세요 \uD83D\uDD90\uD83C\uDFFB") // 평문 id
                                    .build())
                            .data(FcmRequestDTO.Data.builder()
                                    .pendingRequestId(request.getId())
                                    .sender(aes.decrypt(parentEncryptedNickname)) // 평문 id
                                    .type("AccountLinkRequest")
                                    .build())
                            .build();
                    fcmRequestService.sendPush(requestDTO);
                    log.info("연동 요청 푸시알림을 성공적으로 보냈습니다.");
                }
            } else {
                log.warn("연동 요청을 보낼 아이({})의 FcmToken 이 존재하지 않습니다.", childEncryptedNickname);
            }
        } catch (Exception e) {
            throw new CustomException("일시적 오류가 발생하였습니다.", e.toString(), HttpStatus.INTERNAL_SERVER_ERROR, e);
        }

        // 9. 응답 반환
        response.put("success", true);
        response.put("message", "자녀에게 연동 요청이 전송되었습니다.");
        return new ResponseEntity<>(response, HttpStatus.OK);
    }


    /**
     * 부모가 이미 보낸 연동요청이 있는지 체크
     * * status == pending (approved, denied 경우는 클라에서 부모쪽이 확인한 이후 바로 삭제됨)
     */
    public ResponseEntity<Map<String, Object>> getParentPendingStatus(String parentEncryptedNickname) {
        try {
            PendingAccountRequest pendingRequest = accountRepository.findParentRequestByStatus(parentEncryptedNickname, "pending");

            if (pendingRequest != null) {
                String childEncryptedNickname = pendingRequest.getChildEncryptedNickname();
                log.info("암호화된 자녀 닉네임: {}", childEncryptedNickname);

                // 유저가 존재하는지 확인
                userRepository.findByEncryptedNickname(childEncryptedNickname).orElseThrow(() -> new CustomException("아이 사용자를 찾을 수 없습니다", null, HttpStatus.NOT_FOUND, null));

                // 복호화된 닉네임 얻기
                String childDecryptedNickname = aes.decrypt(childEncryptedNickname);
                log.info("복호화된 자녀 닉네임: {}", childDecryptedNickname);

                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "childNickname", childDecryptedNickname,
                        "status", pendingRequest.getStatus()
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

}
