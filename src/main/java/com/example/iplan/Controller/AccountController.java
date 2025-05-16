package com.example.iplan.Controller;

import com.example.iplan.DTO.AccountRequestDTO;
import com.example.iplan.Domain.PendingAccountRequest;
import com.example.iplan.Repository.AccountRepository;
import com.example.iplan.Service.AccountService;
import com.example.iplan.auth.UserRepository;
import com.example.iplan.auth.UserRole;
import com.example.iplan.auth.Users;
import com.example.iplan.auth.oauth2.CustomOAuth2UserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Tag(name = "Account CRUD", description = "계정 연동 관련 api 요청")
@Slf4j
@RequiredArgsConstructor
@Controller
@Validated
@RequestMapping("/api")
public class AccountController {
    private final AccountService accountService;

    @Operation(summary = "부모가 아이 계정 연동 요청을 보냄 POST", description = "해당 요청을 PendingAccountRequest 저장")
    @PostMapping("/parent/link-child")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> parentAccountRequest(@RequestBody @NotNull AccountRequestDTO requestDTO, @AuthenticationPrincipal CustomOAuth2UserDetails user)
            throws ExecutionException, InterruptedException {
        log.info("Account API from parent received!");

        String childNickname = requestDTO.getChildNickname();
        String parentNickname = user.getUsername();

        return accountService.sendAccountRequest(childNickname, parentNickname);
    }

    @GetMapping("/child/pending-requests")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getChildPendingRequests(@AuthenticationPrincipal CustomOAuth2UserDetails user) {
        Map<String, Object> response = new HashMap<>();
        try {
            String childNickname = user.getUsername();
            List<AccountRequestDTO> requests = accountService.getPendingRequestsForChild(childNickname);

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
        }
    }

}
