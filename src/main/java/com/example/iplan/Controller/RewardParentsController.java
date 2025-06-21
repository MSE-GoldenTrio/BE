package com.example.iplan.Controller;

import com.example.iplan.DTO.FeedbackDTO;
import com.example.iplan.Domain.RewardParents;
import com.example.iplan.Service.RewardParentsService;
import com.example.iplan.auth.oauth2.CustomOAuth2UserDetails;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/parent/reward")
public class RewardParentsController {

    private final RewardParentsService rewardParentsService;

    /**
     * 부모의 linked_id를 기반으로 모든 자녀의 전체 보상 목록 반환
     */
    @Operation(summary = "부모의 자녀 전체 보상 목록 조회 GET", description = "해당 부모와 연동된 모든 자녀의 보상 목록을 가져온다.")
    @GetMapping("/list")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getAllChildRewardList(
            @AuthenticationPrincipal CustomOAuth2UserDetails user) throws ExecutionException, InterruptedException {

        List<String> linkedIds = user.getUser().getLinked_id();

        // 예외 처리: 연동된 자녀가 없는 경우
        if (linkedIds == null || linkedIds.isEmpty()) {
            return new ResponseEntity<>(Map.of("success", false, "message", "연동된 자녀가 없습니다."), HttpStatus.BAD_REQUEST);
        }

        Map<String, Object> response = rewardParentsService.getRewardsForAllChildren(linkedIds);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }


}
