package com.example.iplan.Controller;

import com.example.iplan.DTO.AccountRequestDTO;
import com.example.iplan.ExceptionHandler.CustomException;
import com.example.iplan.Service.AccountChildService;
import com.example.iplan.auth.Users;
import com.example.iplan.auth.oauth2.CustomOAuth2UserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Tag(name = "Child Account CRUD", description = "아이들의 계정 연동 관련 api 요청")
@Slf4j
@RequiredArgsConstructor
@RestController
@Validated
@RequestMapping("/api/child")
public class AccountChildController {
    private final AccountChildService accountChildService;

    // 아이가 부모의 요청을 승인 or 거부
    // PendingAccountRequest의 문서 ID와 승인 여부(approved/denied) 를 DTO로 받아옴!!
    @PostMapping("/respond-request")
    public ResponseEntity<?> respondToRequest(@AuthenticationPrincipal CustomOAuth2UserDetails user,
                                              @RequestBody AccountRequestDTO dto) {
        String childEncryptedNickname = user.getUsername();
        try {
            Users users = accountChildService.respondToRequest(childEncryptedNickname, dto);
            log.info("linked id updated: {}", users);
            if (users != null) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "요청이 승인되었습니다."
                ));
            } else {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "요청이 거절되었습니다."
                ));
            }
        } catch (CustomException e) {
            return ResponseEntity.status(e.getStatus()).body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "서버 오류가 발생했습니다."
            ));
        }
    }

    @Operation(summary = "Child FCM POST", description = "아이의 누락된 연동요청 푸시알림 재요청")
    @PostMapping("/push/send-account-link")
    public ResponseEntity<Map<String, Object>> sendAccountRequest(@RequestBody @NotNull AccountRequestDTO requestDTO, @AuthenticationPrincipal CustomOAuth2UserDetails user)
            throws ExecutionException, InterruptedException {
        log.info("아이의 누락된 푸시알림 재요청 도착");

        String childEncryptedNickname = user.getUsername();
        String pendingRequestId = requestDTO.getId();

        return accountChildService.sendAccountRequest(childEncryptedNickname, pendingRequestId);
    }


}
