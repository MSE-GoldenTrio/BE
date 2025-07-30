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
import com.example.iplan.util.AES256Encryptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ExecutionException;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountChildService {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final AES256Encryptor aes;

    /**
     * 아이가 부모의 요청을 승인 or 거부
     * 이때 아이의 linked_id에 이미 부모가 존재한다면 수락 못하도록 !!
     */
    public Users respondToRequest(String encryptedChildNickname, AccountRequestDTO dto)
            throws Exception {

        log.info("연동 요청 응답 서비스");

        // 1. 요청 ID로 PendingAccountRequst 조회 (부모가 요청 보낼 때 저장되어있음)
        PendingAccountRequest request = accountRepository.findEntityByDocumentId(dto.getId());
        if (request == null) {
            throw new CustomException("해당 요청이 존재하지 않습니다.", HttpStatus.NOT_FOUND);
        }

        // 해시값으로 아이 닉네임 비교
        if (!request.getChildHashedNickname().equals(DigestUtils.sha256Hex(aes.decrypt(encryptedChildNickname)))) {
            throw new CustomException("본인의 요청만 처리할 수 있습니다.", HttpStatus.FORBIDDEN);
        }

        String encryptedParentNickname = request.getParentEncryptedNickname();
        log.info("부모의 암호화된 닉네임: {}", encryptedParentNickname);
        log.info("연동 요청 승인 여부: {}", dto.isApproved());

        // 2. 사용자 정보 조회
        Users childUser = userRepository.findByEncryptedNickname(encryptedChildNickname).orElseThrow(() -> new IllegalArgumentException("해당 아이 유저가 존재하지 않습니다."));
        Users parentUser = userRepository.findByEncryptedNickname(encryptedParentNickname).orElseThrow(() -> new IllegalArgumentException("해당 부모 유저가 존재하지 않습니다."));

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

        // 승인하는 경우
        if (dto.isApproved()) {
            request.setApproved(true);
            request.setStatus("approved");
            accountRepository.update(request);

            if (!childUser.getLinked_id().contains(encryptedParentNickname)) {
                childUser.getLinked_id().add(encryptedParentNickname);
            }
            if (!parentUser.getLinked_id().contains(encryptedChildNickname)) {
                parentUser.getLinked_id().add(encryptedChildNickname);
            }
            userRepository.update(childUser);
            userRepository.update(parentUser);

            log.info("부모-자녀 연결 완료: {} <-> {}", encryptedParentNickname, encryptedChildNickname);

            return childUser;
        } else {
            request.setApproved(false);
            request.setStatus("denied");
            accountRepository.update(request);
            log.info("연동 요청 거절 완료");
            return null; // 거절 시 토큰 재발급 없음
        }
    }


}
