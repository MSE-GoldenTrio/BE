package com.example.iplan.Service;

import com.example.iplan.Domain.PendingAccountRequest;
import com.example.iplan.DTO.AccountRequestDTO;
import com.example.iplan.ExceptionHandler.CustomException;
import com.example.iplan.Repository.AccountRepository;
import com.example.iplan.auth.UserRepository;
import com.example.iplan.auth.Users;
import com.example.iplan.auth.oauth2.CustomOAuth2UserDetails;
import com.example.iplan.auth.jwt.JwtToken;
import com.example.iplan.auth.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountChildService {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;

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

    /**
     * 아이가 부모의 요청을 승인 or 거부
     * 이때 아이의 linked_id에 이미 부모가 존재한다면 수락 못하도록 !!
     */
    public String respondToRequest(String childNickname, AccountRequestDTO dto)
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

        // 2. 사용자 정보 조회
        Users childUser = userRepository.findByFields(Map.of("nickname", childNickname));
        Users parentUser = userRepository.findByFields(Map.of("nickname", parentNickname));

        if (childUser == null || parentUser == null) {
            throw new CustomException("유저 정보를 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
        }

        List<String> linkedIds = childUser.getLinked_id();
        if (dto.isApproved() && linkedIds != null && !linkedIds.isEmpty()) {
            log.info("이미 다른 계정과 연동되어 있어 수락 불가");
            request.setApproved(false);
            request.setStatus("denied");
            accountRepository.update(request);
            throw new CustomException("이미 다른 계정과 연동되어 있어 수락할 수 없습니다.", HttpStatus.BAD_REQUEST);
        }

        if (dto.isApproved()) {
            request.setApproved(true);
            request.setStatus("approved");
            accountRepository.update(request);

            if (!childUser.getLinked_id().contains(parentNickname)) {
                childUser.getLinked_id().add(parentNickname);
            }
            if (!parentUser.getLinked_id().contains(childNickname)) {
                parentUser.getLinked_id().add(childNickname);
            }
            userRepository.update(childUser);
            userRepository.update(parentUser);

            log.info("부모-자녀 연결 완료: {} <-> {}", parentNickname, childNickname);

            // 연동 완료된 childUser로 토큰 재발급
            CustomOAuth2UserDetails userDetails = new CustomOAuth2UserDetails(childUser);
            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities()
            );
            JwtToken newToken = jwtTokenProvider.generateToken(authentication);
            log.info("계정 연동으로 child 토큰 재발급: {}", newToken);
            return newToken.getAccessToken();
        } else {
            request.setApproved(false);
            request.setStatus("denied");
            accountRepository.update(request);
            log.info("연동 요청 거절 완료");
            return null; // 거절 시 토큰 재발급 없음
        }
    }


}
