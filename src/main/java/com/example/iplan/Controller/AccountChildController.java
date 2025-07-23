package com.example.iplan.Controller;

import com.example.iplan.DTO.AccountRequestDTO;
import com.example.iplan.ExceptionHandler.CustomException;
import com.example.iplan.Service.AccountChildService;
import com.example.iplan.auth.oauth2.CustomOAuth2UserDetails;
import io.swagger.v3.oas.annotations.tags.Tag;
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

    @GetMapping("/pending-requests")
    public ResponseEntity<Map<String, Object>> getChildPendingRequests(@AuthenticationPrincipal CustomOAuth2UserDetails user) {
        Map<String, Object> response = new HashMap<>();
        log.info("Account API from child received!");
        try {
            String childNickname = user.getUsername();
            List<AccountRequestDTO> requests = accountChildService.getPendingRequestsForChild(childNickname);

            if (requests.isEmpty()) {
                response.put("success", false);
                response.put("message", "도착한 연동 요청이 없습니다.");
            } else {
                response.put("success", true);
                response.put("requests", requests);
            }

            return ResponseEntity.ok(response);
        } catch (ExecutionException | InterruptedException e) {
            response.put("success", false);
            response.put("message", "연동 요청을 가져오는 데 실패했습니다. Error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @PostMapping("/respond-request")
    public ResponseEntity<?> respondToRequest(@AuthenticationPrincipal CustomOAuth2UserDetails user,
                                              @RequestBody AccountRequestDTO dto) {
        try {
            String newToken = accountChildService.respondToRequest(user.getUsername(), dto);
            log.info("새로운 토큰: {}", newToken);
            if (newToken != null) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "요청이 승인되었으며 토큰이 갱신되었습니다.",
                        "token", newToken
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


}
