package com.example.iplan.Service;

import com.example.iplan.Domain.PendingAccountRequest;
import com.example.iplan.DTO.AccountRequestDTO;
import com.example.iplan.ExceptionHandler.CustomException;
import com.example.iplan.Repository.AccountRepository;
import com.example.iplan.auth.UserRepository;
import com.example.iplan.auth.Users;
import com.example.iplan.fcm.FcmRequestDTO;
import com.example.iplan.fcm.FcmRequestService;
import com.example.iplan.fcm.FcmToken;
import com.example.iplan.fcm.FcmTokenService;
import com.example.iplan.util.AES256Encryptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
public class AccountChildService {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final AES256Encryptor aes;
    private final FcmRequestService fcmRequestService;
    private final FcmTokenService fcmTokenService;

    /**
     * 아이가 부모의 요청을 승인 or 거부
     * 이때 아이의 linked_id에 이미 부모가 존재한다면 수락 못하도록 !!
     */
    public Users respondToRequest(String childEncryptedNickname, AccountRequestDTO dto)
            throws Exception {

        log.info("아이의 연동 요청 응답 서비스");

        // 1. DTO에서 받아온 요청 ID로 PendingAccountRequest 조회 (부모가 요청 보낼 때 PendingAccountRequest 저장)
        PendingAccountRequest request = accountRepository.findEntityByDocumentId(dto.getId());
        if (request == null) {
            throw new CustomException("일시적 오류가 발생하였습니다.","해당 요청:"+dto.getId()+"이 존재하지 않습니다.", HttpStatus.NOT_FOUND, null);
        }

        // 2. (암호화된) 아이 닉네임 비교
        if (!request.getChildEncryptedNickname().equals(childEncryptedNickname)) {
            throw new CustomException("본인의 요청만 처리할 수 있습니다.", null, HttpStatus.FORBIDDEN, null);
        }
        log.info("아이의 암호화된 닉네임: {}", childEncryptedNickname);

        String parentEncryptedNickname = request.getParentEncryptedNickname();
        log.info("부모의 암호화된 닉네임: {}", parentEncryptedNickname);
        log.info("연동 요청 승인 여부: {}", dto.isApproved());

        // 3. 사용자 정보 조회
        Users childUser = userRepository.findByEncryptedNickname(childEncryptedNickname).orElseThrow(() -> new IllegalArgumentException("해당 아이 유저가 존재하지 않습니다."));
        Users parentUser = userRepository.findByEncryptedNickname(parentEncryptedNickname).orElseThrow(() -> new IllegalArgumentException("해당 부모 유저가 존재하지 않습니다."));
        if (childUser == null || parentUser == null) {
            throw new CustomException("유저 정보를 찾을 수 없습니다.", null, HttpStatus.NOT_FOUND, null);
        }

        // 4. 아이의 linked_id가 이미 존재하는데 approved(승인)한 경우 -> 수락 불가(denied)
        List<String> linkedIds = childUser.getLinked_id();
        if (dto.isApproved() && linkedIds != null && !linkedIds.isEmpty()) {
            log.info("이미 다른 계정과 연동되어 있어 수락 불가");
            request.setApproved(false);
            request.setStatus("denied");
            accountRepository.update(request);
            throw new CustomException("이미 다른 계정과 연동되어 있어 수락할 수 없습니다.", null, HttpStatus.BAD_REQUEST, null);
        }

        // 5. 승인하는 경우
        if (dto.isApproved()) {
            request.setApproved(true);
            request.setStatus("approved");
            accountRepository.update(request);

            // 아이와 부모의 linked_id에 각각 연동할 (암호화된) 닉네임이 없는 경우에만 추가하기
            if (!childUser.getLinked_id().contains(parentEncryptedNickname)) {
                childUser.getLinked_id().add(parentEncryptedNickname);
            }
            if (!parentUser.getLinked_id().contains(childEncryptedNickname)) {
                parentUser.getLinked_id().add(childEncryptedNickname);
            }

            // 아이와 부모 유저 linked_id 업데이트
            userRepository.update(childUser);
            userRepository.update(parentUser);

            log.info("부모-자녀 연결 완료: {} <-> {}", parentEncryptedNickname, childEncryptedNickname);

            return childUser;
        } else {
            // 6. 거절하는 경우
            request.setApproved(false);
            request.setStatus("denied");
            accountRepository.update(request);
            log.info("연동 요청 거절 완료");
            return null;
        }
    }

    /**
     * 아이에게 누락된 연동 요청 푸시알림 (FCM) 다시 보냄
     */
    public ResponseEntity<Map<String, Object>> sendAccountRequest(String childEncryptedNickname, String pendingRequestId)
            throws ExecutionException, InterruptedException {

        Map<String, Object> response = new HashMap<>();

        // 자녀 유저 조회
        Users childUser = userRepository.findByEncryptedNickname(childEncryptedNickname)
                .orElseThrow(() -> new CustomException("해당 닉네임의 자녀를 찾을 수 없습니다.", null, HttpStatus.NOT_FOUND, null));

        // 해당 요청 가져오기
        PendingAccountRequest accountRequest = accountRepository.findByRequestId(pendingRequestId);
        String parentEncryptedNickname = accountRequest.getParentEncryptedNickname();

        // 이미 다른 부모와 연동된 자녀인지 확인
        log.info("이미 연동된 자녀인지 체크..");
        List<String> linkedIds = childUser.getLinked_id();
        if (linkedIds != null && linkedIds.stream().anyMatch(Objects::nonNull)) {
            // PendingAccountRequest denied로 업데이트
            accountRequest.setStatus("denied");
            accountRequest.setApproved(false);
            accountRepository.update(accountRequest);

            response.put("success", false);
            response.put("message", "해당 자녀 계정은 이미 다른 계정과 연동되어 있습니다.");
            return new ResponseEntity<>(response, HttpStatus.OK);
        }

        // 연동 요청을 보낼 아이의 FcmToken 찾기
        List<FcmToken> fcmTokens = fcmTokenService.getTokensByHashedUserId(childUser.getNicknameHash()); //아이디 해시값으로 찾기

        try {
            if(!fcmTokens.isEmpty()){
                // 5. 아이의 존재하는 모든 FcmToken 으로 푸시알림 전송
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
                                    .pendingRequestId(pendingRequestId)
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

        // 응답 반환
        response.put("success", true);
        response.put("message", "자녀에게 연동 요청이 전송되었습니다.");
        return new ResponseEntity<>(response, HttpStatus.OK);
    }


}
