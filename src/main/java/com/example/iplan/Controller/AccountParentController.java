package com.example.iplan.Controller;

import com.example.iplan.DTO.AccountRequestDTO;
import com.example.iplan.Domain.PendingAccountRequest;
import com.example.iplan.Repository.AccountRepository;
import com.example.iplan.Service.AccountParentService;
import com.example.iplan.auth.oauth2.CustomOAuth2UserDetails;
import io.swagger.v3.oas.annotations.Operation;
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

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Tag(name = "Parent Account CRUD", description = "부모님의 계정 연동 관련 api 요청")
@Slf4j
@RequiredArgsConstructor
@RestController
@Validated
@RequestMapping("/api/parent")
public class AccountParentController {
    private final AccountParentService accountParentService;
    private final AccountRepository accountRepository;

    @Operation(summary = "부모가 아이 계정 연동 요청을 보냄 POST", description = "해당 요청을 PendingAccountRequest 저장")
    @PostMapping("/link-child")
    public ResponseEntity<Map<String, Object>> parentAccountRequest(@RequestBody @NotNull AccountRequestDTO requestDTO, @AuthenticationPrincipal CustomOAuth2UserDetails user)
            throws ExecutionException, InterruptedException {
        log.info("Account API from parent received!");

        String childNickname = requestDTO.getChildNickname();
        String parentNickname = user.getUsername();

        return accountParentService.sendAccountRequest(childNickname, parentNickname);
    }

    @GetMapping("/check-pending-status")
    public ResponseEntity<Map<String, Object>> checkPendingStatus(@AuthenticationPrincipal CustomOAuth2UserDetails user) {
        log.info("Check pending status API from parent received");
        String parentNickname = user.getUser().getNickname();
        return accountParentService.getParentPendingStatus(parentNickname);
    }

    @GetMapping("/check-linked")
    public ResponseEntity<Map<String, Object>> checkIfLinked(@RequestParam String childNickname, @AuthenticationPrincipal CustomOAuth2UserDetails user) throws ExecutionException, InterruptedException {
        log.info("Account linked check API from parent received");

        String parentNickname = user.getUsername();
        log.info("Parent Nickname:{}", parentNickname);

        return accountParentService.checkParentLinkedId(childNickname, parentNickname);
    }

    @DeleteMapping("/delete-denied-request")
    public ResponseEntity<Map<String, Object>> deleteDeniedRequest(
            @AuthenticationPrincipal CustomOAuth2UserDetails user) {
        log.info("Delete denied request API from parent received");

        return accountParentService.deleteDeniedRequest(user.getUser().getNickname());
    }

}
