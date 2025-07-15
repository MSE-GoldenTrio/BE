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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    /**
     * 부모가 자녀 닉네임을 입력하여 연동 요청을 보냄
     */
    public ResponseEntity<Map<String, Object>> sendAccountRequest(String childNickname, String parentNickname)
            throws ExecutionException, InterruptedException {

        Map<String, Object> response = new HashMap<>();

        // 1. 자녀 유저 조회
        Users childUser = userRepository.findByNickname(childNickname)
                .orElseThrow(() -> new CustomException("해당 닉네임의 자녀를 찾을 수 없습니다.", HttpStatus.NOT_FOUND));

        // 2. 자녀인지 확인
        if (childUser.getAuthority() != UserRole.CHILD) {
            throw new CustomException("입력한 닉네임은 자녀 계정이 아닙니다.", HttpStatus.BAD_REQUEST);
        }

        // 3. 이미 연동된 자녀인지 확인
        log.info("이미 연동된 자녀인지 체크..");
        List<String> linkedIds = childUser.getLinked_id();
        if (linkedIds != null && linkedIds.stream().anyMatch(Objects::nonNull)) {
            response.put("success", false);
            response.put("message", "해당 자녀 계정은 이미 다른 계정과 연동되어 있습니다.");
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }

        log.info("수락되지 않은 동일한 요청 체크..");
        // 4. 수락되지 않은 동일한 요청이 이미 존재하는지 확인
        PendingAccountRequest existingRequest = accountRepository.findExistingRequest(childNickname, parentNickname);
        if (existingRequest != null) {
            response.put("success", false);
            response.put("message", "이미 해당 자녀에게 연동 요청을 보냈습니다. 요청이 승인될 때까지 기다려주세요.");
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }

        log.info("이미 해당 계정과 연동이 되어있는지 체크..");
        // 5. 이미 해당 계정과 연동이 되어있는지 확인
        PendingAccountRequest approvedRequest = accountRepository.findApprovedRequest(childNickname, parentNickname);
        if (approvedRequest != null) {
            response.put("success", false);
            response.put("message", "이미 해당 자녀와 연동이 되어있습니다.");
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }

        // 5. PendingAccountRequest 생성
        PendingAccountRequest request = PendingAccountRequest.builder()
                .childNickname(childNickname)
                .parentNickname(parentNickname)
                .approved(false)
                .status("pending")
                .build();

        // 6. 저장
        accountRepository.saveWithAutoIncrement(request);

        // 7. 응답 반환
        response.put("success", true);
        response.put("message", "자녀에게 연동 요청이 전송되었습니다.");
        return new ResponseEntity<>(response, HttpStatus.OK);
    }


    /**
     * 부모가 이미 보낸 연동요청이 있는지 체크
     */
    public ResponseEntity<Map<String, Object>> getParentPendingStatus(String parentNickname) {
        try {
            PendingAccountRequest pendingRequests = accountRepository.findParentRequest(parentNickname);

            if (pendingRequests != null) {
                String childNickname = pendingRequests.getChildNickname();
                log.info("Child Nickname: {}", childNickname);
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "childNickname", childNickname
                ));
            } else {
                return ResponseEntity.ok(Map.of("status", "none"));
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", "false",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * 부모의 요청이 승인되었는지 linked_id 확인
     * 만약 승인되었다면 토큰 재발급 후 페이지 이동
     * 만약 거절되었다면 오류 응답 반환
     */
    public ResponseEntity<Map<String, Object>> checkParentLinkedId(String childNickname, String parentNickname)
            throws ExecutionException, InterruptedException {

        try {
            // 1. 요청이 승인된 경우
            PendingAccountRequest accountRequest = accountRepository.findByChildNicknameAndParentNickname(childNickname, parentNickname);
            if (accountRequest != null && accountRequest.isApproved() && Objects.equals(accountRequest.getStatus(), "approved")) {
                // 사용자 정보 조회
                Users parentUser = userRepository.findByField("nickname", parentNickname);
                // 연동 완료된 parentUser로 토큰 재발급
                CustomOAuth2UserDetails userDetails = new CustomOAuth2UserDetails(parentUser);
                Authentication authentication = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities()
                );
                JwtToken newToken = jwtTokenProvider.generateToken(authentication);
                log.info("요청 승인됨.. 토큰 재발급 완료");
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "status", "approved",
                        "message", String.format("%s 계정에 대한 연동 요청이 승인되었습니다.", childNickname),
                        "token", newToken
                ));
            }
            // 2. 요청이 거부된 경우
            else if (accountRequest != null && !accountRequest.isApproved() && Objects.equals(accountRequest.getStatus(), "denied")) {
                log.info("요청 거부됨");
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "status", "denied",
                        "message", String.format("%s 계정에 대한 연동 요청이 거부되었습니다.", childNickname)
                ));
            }
            // 3. 아직 요청이 승인이 안 된 경우
            else if (accountRequest != null && !accountRequest.isApproved() && Objects.equals(accountRequest.getStatus(), "pending")) {
                log.info("아직 요청이 승인 안됨");
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "status", "pending",
                        "message", String.format("%s 계정에 대한 연동 요청이 승인될 때까지 기다려주세요.", childNickname)
                ));
            }
            return null;
        } catch (Exception e) {
            throw new ExecutionException("계정 연동 요청을 가져오는 중 오류가 발생했습니다.", e);
        }
    }

    /**
     * 연동 요청이 거부된 것은 부모가 확인 후에 엔티티에서 삭제
     */
    public ResponseEntity<Map<String, Object>> deleteDeniedRequest(String parentNickname) {
        try {
            PendingAccountRequest deniedRequest = accountRepository.findByFields(
                    Map.of(
                            "parentNickname", parentNickname,
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
