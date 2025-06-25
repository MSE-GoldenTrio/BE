package com.example.iplan.Controller;

import com.example.iplan.DTO.FeedbackDTO;
import com.example.iplan.Domain.Feedback;
import com.example.iplan.Domain.RewardParents;
import com.example.iplan.Service.FeedbackService;
import com.example.iplan.auth.oauth2.CustomOAuth2UserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/parent/feedback")
public class FeedbackController {

    private final FeedbackService feedbackService;

    // 아이들이 설정한 보상 지급
    @PostMapping
    public ResponseEntity<Map<String, Object>> addFeedback(@RequestBody FeedbackDTO feedbackDTO, @AuthenticationPrincipal CustomOAuth2UserDetails user)
            throws ExecutionException, InterruptedException {

        String parentNickname = user.getUsername();
        return feedbackService.saveFeedback(feedbackDTO, parentNickname);
    }

    // 부모가 해당하는 아이에 대한 모든 피드백 가져옴 (child, parent 요청 모두 가능)
    @GetMapping("/list/{childNickname}")
    public ResponseEntity<Map<String, Object>> getFeedbackParents(@AuthenticationPrincipal CustomOAuth2UserDetails user, @PathVariable String childNickname)
            throws ExecutionException, InterruptedException {

        log.info("피드백 get 요청 도착!!");

        String parentNickname;
        // 자녀 계정인 경우
        if (user.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_CHILD"))) {
            List<String> linkedIds = user.getUser().getLinked_id();
            if (linkedIds == null || linkedIds.isEmpty()) {
                // 연동된 부모가 없을 때
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "연동된 부모 정보가 없습니다.");
                return new ResponseEntity<>(errorResponse, HttpStatus.OK);
            }
            parentNickname = linkedIds.get(0);
        } else {
            // 부모 계정인 경우
            parentNickname = user.getUsername();
        }
        log.info("부모 아이디: {}", parentNickname);
        Map<String, Object> response = feedbackService.getFeedbackParents(childNickname, parentNickname);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @PatchMapping
    public ResponseEntity<Map<String, Object>> updateRewardParents(@RequestBody FeedbackDTO rewardParents) throws ExecutionException, InterruptedException {
        return feedbackService.updateRewardParents(rewardParents);
    }
}
