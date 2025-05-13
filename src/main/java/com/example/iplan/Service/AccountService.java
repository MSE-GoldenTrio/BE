package com.example.iplan.Service;

import com.example.iplan.Domain.PendingAccountRequest;
import com.example.iplan.DTO.AccountRequestDTO;
import com.example.iplan.ExceptionHandler.CustomException;
import com.example.iplan.Repository.AccountRepository;
import com.example.iplan.auth.UserRepository;
import com.example.iplan.auth.UserRole;
import com.example.iplan.auth.Users;
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

        // 3. PendingAccountRequest 생성
        PendingAccountRequest request = PendingAccountRequest.builder()
                .childNickname(childNickname)
                .parentNickname(parentNickname)
                .approved(false)
                .build();

        // 4. 저장
        accountRepository.saveWithAutoIncrement(request);

        // 5. 응답 반환
        response.put("success", true);
        response.put("message", "자녀에게 연동 요청이 전송되었습니다.");
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    /**
     * 자녀가 부모의 연동 요청을 확인
     * 아직 승인되지 않은 요청만 필터링 (approved == false)
     * 반환값은 List<AccountRequestDTO> 형태
     */
    public List<AccountRequestDTO> getPendingRequestsForChild(String childNickname) throws ExecutionException, InterruptedException {
        // 자녀가 존재하는지 확인
        Users user = userRepository.findByNickname(childNickname)
                .orElseThrow(() -> new CustomException("자녀 닉네임에 해당하는 사용자가 존재하지 않습니다.", HttpStatus.NOT_FOUND));

        log.info("Get pending account requests for child [{}] started!", childNickname);

        try {
            // 승인되지 않은 요청만 필터링
            List<PendingAccountRequest> pendingList = accountRepository.findByChildNicknameAndApproved(childNickname, false);

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


}
