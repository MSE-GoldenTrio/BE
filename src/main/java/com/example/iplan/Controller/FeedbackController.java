package com.example.iplan.Controller;

import com.example.iplan.DTO.FeedbackDTO;
import com.example.iplan.DTO.RewardChildDTO;
import com.example.iplan.Domain.Feedback;
import com.example.iplan.Domain.RewardParents;
import com.example.iplan.Service.FeedbackService;
import com.example.iplan.auth.UserRepository;
import com.example.iplan.auth.Users;
import com.example.iplan.auth.oauth2.CustomOAuth2UserDetails;
import com.google.firebase.database.annotations.NotNull;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
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
@RequestMapping
public class FeedbackController {

    private final FeedbackService feedbackService;
    private final UserRepository userRepository;

    // 아이들이 설정한 보상 지급
    @PostMapping("/parent/feedback")
    public ResponseEntity<Map<String, Object>> addFeedback(@RequestBody FeedbackDTO feedbackDTO, @AuthenticationPrincipal CustomOAuth2UserDetails user)
            throws ExecutionException, InterruptedException {

        String parentNickname = user.getUsername();
        return feedbackService.saveFeedback(feedbackDTO, parentNickname);
    }

    // 피드백 수정
    @PatchMapping("/parent/feedback/update")
    public ResponseEntity<Map<String, Object>> updateReward(@RequestBody FeedbackDTO feedbackDTO, @AuthenticationPrincipal CustomOAuth2UserDetails user)
            throws ExecutionException, InterruptedException {

        String parentNickname = user.getUsername();
        return feedbackService.updateFeedback(feedbackDTO, parentNickname);
    }

    // 부모의 linked_id를 기반으로 모든 자녀의 전체 피드백 목록 반환
    @Operation(summary = "부모의 자녀 전체 피드백 목록 조회 GET", description = "해당 부모와 연동된 모든 자녀의 피드백 목록을 가져온다.")
    @GetMapping("/parent/feedback/list")
    public ResponseEntity<Map<String, Object>> getAllFeedbackList(
            @AuthenticationPrincipal CustomOAuth2UserDetails user) throws ExecutionException, InterruptedException {

        String parentNickname = user.getUsername();
        List<String> linkedIds = user.getUser().getLinked_id();

        // 예외 처리: 연동된 자녀가 없는 경우
        if (linkedIds == null || linkedIds.isEmpty()) {
            return new ResponseEntity<>(Map.of("success", false, "message", "연동된 자녀가 없습니다."), HttpStatus.BAD_REQUEST);
        }

        return feedbackService.getAllFeedbacks(parentNickname);
    }

    // 부모가 해당하는 아이에 대한 모든 피드백 가져옴 (child, parent 요청 모두 가능)
    @GetMapping("/feedback/list/{childNickname}")
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
            parentNickname = linkedIds.get(0); // 평문으로 가져와야됨
            Users parentUser = userRepository.findByHashValueNickName(DigestUtils.sha256Hex(parentNickname)).orElseThrow(()
                    -> new IllegalArgumentException("User not found"));
            parentNickname = parentUser.getNickname();
        } else {
            // 부모 계정인 경우
            parentNickname = user.getUsername(); // 암호화된 아이디여야함
        }
        log.info("부모 아이디: {}", parentNickname);
        Map<String, Object> response = feedbackService.getOneChildFeedback(childNickname, parentNickname);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    // 단일 피드백 목록 반환 -> 프론트에서 onSnapshot()으로 데이터 변경 감지 후 바뀐 피드백만 가져오는것
    // 아이가 요청하는 경우만 해당
    @Operation(summary = "onSnapshot()으로 감지한 바뀐 피드백 데이터 GET", description = "단일 피드백 목록을 가져온다.")
    @GetMapping("/feedback/changed")
    public ResponseEntity<Map<String, Object>> getChangedFeedback(@AuthenticationPrincipal CustomOAuth2UserDetails user, @RequestParam("feedbackId") String feedbackId) throws ExecutionException, InterruptedException {
        String childNickname = user.getUsername();
        log.info("변경된 피드백 ID: {}", feedbackId);
        Map<String, Object> response = feedbackService.getFeedbackById(childNickname, feedbackId);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

}
