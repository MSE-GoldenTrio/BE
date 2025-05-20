package com.example.iplan.Service;

import com.example.iplan.Domain.PendingAccountRequest;
import com.example.iplan.DTO.AccountRequestDTO;
import com.example.iplan.ExceptionHandler.CustomException;
import com.example.iplan.Repository.AccountRepository;
import com.example.iplan.auth.UserRepository;
import com.example.iplan.auth.UserRole;
import com.example.iplan.auth.Users;
import com.example.iplan.auth.oauth2.CustomOAuth2UserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;

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
        if (childUser.getLinked_id() != null && !childUser.getLinked_id().isEmpty()) {
            response.put("success", false);
            response.put("message", "해당 자녀 계정은 이미 다른 계정과 연동되어 있습니다.");
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }

        // 4. 수락되지 않은 동일한 요청이 이미 존재하는지 확인
        PendingAccountRequest existingRequest = accountRepository.findExistingRequest(childNickname, parentNickname);
        if (existingRequest != null) {
            response.put("success", false);
            response.put("message", "이미 해당 자녀에게 연동 요청을 보냈습니다. 요청이 승인될 때까지 기다려주세요.");
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }

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
     * 자녀가 부모의 연동 요청을 확인
     * 아직 승인되지 않은 요청만 필터링 (approved == false && status == pending)
     * 반환값은 List<AccountRequestDTO> 형태
     */
    public List<AccountRequestDTO> getPendingRequestsForChild(String childNickname) throws ExecutionException, InterruptedException {
        // 1. 자녀가 존재하는지 확인
        Users user = userRepository.findByNickname(childNickname)
                .orElseThrow(() -> new CustomException("자녀 닉네임에 해당하는 사용자가 존재하지 않습니다.", HttpStatus.NOT_FOUND));

        log.info("Get pending account requests for child [{}] started!", childNickname);

        try {
            // 2. 승인되지 않은 pending 요청만 필터링
            List<PendingAccountRequest> pendingList = accountRepository.findByChildNicknameAndApprovedAndStatus(childNickname, false, "pending");

            if (pendingList.isEmpty()) {
                log.info("No pending account requests found for child: {}", childNickname);
            } else {
                log.info("Pending account requests retrieved. Count: {}", pendingList.size());
            }

            // DTO 변환 후 반환
            return pendingList.stream()
                    .map(accountRepository::convertToDTO)
                    .toList();

        } catch (Exception e) {
            log.error("Error while fetching account requests for child [{}]: {}", childNickname, e.getMessage());
            throw new ExecutionException("계정 연동 요청을 가져오는 중 오류가 발생했습니다.", e);
        }
    }

    public void respondToRequest(String childNickname, AccountRequestDTO dto)
            throws ExecutionException, InterruptedException {

        log.info("연동 요청 응답 서비스");

        // 1. 요청 ID로 단일 요청 조회
        PendingAccountRequest request = accountRepository.findEntityByDocumentId(dto.getId());
        if (request == null) {
            throw new CustomException("해당 요청이 존재하지 않습니다.", HttpStatus.NOT_FOUND);
        }

        if (!request.getChildNickname().equals(childNickname)) {
            throw new CustomException("본인의 요청만 처리할 수 있습니다.", HttpStatus.FORBIDDEN);
        }

        String parentNickname = request.getParentNickname();
        log.info("Parent Nickname: {}", parentNickname);
        log.info("연동 요청 승인 여부: {}", dto.isApproved());

        if (dto.isApproved()) {
            // 1. PendingAccountRequest 요청 상태 업데이트 (approved -> true && status -> approved)
            request.setApproved(true);
            request.setStatus("approved");
            accountRepository.update(request);

            // 2. 사용자 linked_id 업데이트
            Users childUser = userRepository.findByFields(Map.of("nickname", childNickname));
            Users parentUser = userRepository.findByFields(Map.of("nickname", parentNickname));

            if (childUser == null || parentUser == null) {
                throw new CustomException("유저 정보를 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
            }

            log.info("Child: {}, Parent: {}", childUser, parentUser);

            // 서로의 linked_id 설정
            childUser.setLinked_id(parentNickname);
            parentUser.setLinked_id(childNickname);
            userRepository.update(childUser);
            userRepository.update(parentUser);

            log.info("부모-자녀 연결 완료: {} <-> {}", parentNickname, childNickname);

        } else {
            // 거절 시 approved = false, status = denied
            request.setApproved(false);
            request.setStatus("denied");
            accountRepository.update(request);

            log.info("연동 요청 거절 완료");
        }
    }





}
