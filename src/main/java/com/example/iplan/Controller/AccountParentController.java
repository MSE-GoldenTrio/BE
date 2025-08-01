package com.example.iplan.Controller;

import com.example.iplan.DTO.AccountRequestDTO;
import com.example.iplan.Repository.AccountRepository;
import com.example.iplan.Service.AccountParentService;
import com.example.iplan.auth.UserRepository;
import com.example.iplan.auth.oauth2.CustomOAuth2UserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

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
    private final UserRepository userRepository;

    // 부모가 아이에게 연동 요청 보냄
    // DTO에서 childNickname만 받아옴!!
    @Operation(summary = "부모가 아이 계정 연동 요청을 보냄 POST", description = "해당 요청을 PendingAccountRequest 저장")
    @PostMapping("/link-child")
    public ResponseEntity<Map<String, Object>> parentAccountRequest(@RequestBody @NotNull AccountRequestDTO requestDTO, @AuthenticationPrincipal CustomOAuth2UserDetails user)
            throws ExecutionException, InterruptedException {
        log.info("부모의 계정 연동 요청 도착");

        String childNickname = requestDTO.getChildNickname();
        String parentEncryptedNickname = user.getUsername();

        return accountParentService.sendAccountRequest(childNickname, parentEncryptedNickname);
    }

    // 부모가 이미 보낸 연동요청이 있는지 체크
    @GetMapping("/check-pending-status")
    public ResponseEntity<Map<String, Object>> checkPendingStatus(@AuthenticationPrincipal CustomOAuth2UserDetails user) {
        log.info("부모가 이미 보낸 연동요청이 있는지 체크");
        String parentEncryptedNickname = user.getUsername();
        return accountParentService.getParentPendingStatus(parentEncryptedNickname);
    }

}
