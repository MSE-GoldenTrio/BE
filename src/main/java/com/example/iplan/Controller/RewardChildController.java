package com.example.iplan.Controller;

import com.example.iplan.DTO.RewardChildDTO;
import com.example.iplan.Domain.RewardChild;
import com.example.iplan.Service.RewardChildService;
import com.google.firebase.database.annotations.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@RequiredArgsConstructor
@Controller
@RequestMapping("/reward-child")
public class RewardChildController {

    private final RewardChildService rewardChildService;

    /**
     * 보상을 추가(저장)
     * @param reward Reward 객체
     * @return 성공 여부 및 오류 메시지
     * @throws ExecutionException
     * @throws InterruptedException
     */
    @PostMapping
    @ResponseBody
    public ResponseEntity<Map<String, Object>> addReward(@RequestBody @NotNull RewardChildDTO reward) throws ExecutionException, InterruptedException {
        return rewardChildService.saveReward(reward);
    }

    /**
     * 보상 세부사항을 가져옴
     * @param documentId 보상 ID
     * @return 보상 객체
     * @throws ExecutionException
     * @throws InterruptedException
     */
    @GetMapping("/{documentId}")
    @ResponseBody
    public ResponseEntity<RewardChild> getReward(@PathVariable String documentId) throws ExecutionException, InterruptedException {
        RewardChild reward = rewardChildService.getReward(documentId);
        return ResponseEntity.ok(reward);
    }

    /**
     * 보상을 수정
     * @param reward 수정할 Reward 객체
     * @return 성공 여부 및 오류 메시지
     * @throws ExecutionException
     * @throws InterruptedException
     */
    @PatchMapping()
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updateReward(@AuthenticationPrincipal String user_id, @RequestBody @NotNull RewardChildDTO reward) throws ExecutionException, InterruptedException {
        return rewardChildService.updateReward(user_id, reward);
    }

    /**
     * 보상을 삭제
     * @param documentID 보상 ID
     * @return 성공 여부 및 오류 메시지
     * @throws ExecutionException
     * @throws InterruptedException
     */
    @DeleteMapping("/{documentID}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteReward(@PathVariable String documentID) throws ExecutionException, InterruptedException {
        return rewardChildService.deleteReward(documentID);
    }

    /**
     * 한 달간 작성한 총 보상의 개수
     * @param user_id 사용자 ID
     * @param year 해당 연도
     * @param month 해당 월 (1월은 1, 12월은 12)
     * @return 한 달간 작성한 총 보상의 개수
     */
    @GetMapping("/monthly-total-rewards-count")
    public ResponseEntity<Map<String, Object>> countMonthlyTotalRewarded(
            @RequestParam String user_id,
            @RequestParam int year,
            @RequestParam int month) {
        Map<String, Object> response = new HashMap<>();
        try {
            int rewardCount = rewardChildService.countMonthlyTotalRewarded(user_id, year, month);
            response.put("success", true);
            response.put("rewardCount", rewardCount);
            return ResponseEntity.ok(response);
        } catch (ExecutionException | InterruptedException e) {
            response.put("success", false);
            response.put("message", "보상 계산에 실패했습니다. Error: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    // 한 달간 달성한 보상 수를 계산 -> success 가 true
    @GetMapping("/monthly-success-rewards-count")
    public ResponseEntity<Map<String, Object>> countMonthlySuccessRewarded(
            @RequestParam String user_id,
            @RequestParam int year,
            @RequestParam int month) {
        Map<String, Object> response = new HashMap<>();
        try {
            int rewardCount = rewardChildService.countMonthlySuccessRewarded(user_id, year, month);
            response.put("success", true);
            response.put("rewardCount", rewardCount);
            return ResponseEntity.ok(response);
        } catch (ExecutionException | InterruptedException e) {
            response.put("success", false);
            response.put("message", "보상 계산에 실패했습니다. Error: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    // 한 달간 첨삭이 완료된 보상 수를 계산 -> rewarded 가 true
    @GetMapping("/monthly-rewarded-count")
    public ResponseEntity<Map<String, Object>> countMonthlyRewarded(
            @RequestParam String user_id,
            @RequestParam int year,
            @RequestParam int month) {
        Map<String, Object> response = new HashMap<>();
        try {
            int rewardCount = rewardChildService.countMonthlyRewarded(user_id, year, month);
            response.put("success", true);
            response.put("rewardCount", rewardCount);
            return ResponseEntity.ok(response);
        } catch (ExecutionException | InterruptedException e) {
            response.put("success", false);
            response.put("message", "보상 계산에 실패했습니다. Error: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    // 한 달간 아직 첨삭이 완료되지 않은 보상 수를 계산 -> rewarded 가 false
    @GetMapping("/monthly-not-rewarded-count")
    public ResponseEntity<Map<String, Object>> countMonthlyNotRewarded(
            @RequestParam String user_id,
            @RequestParam int year,
            @RequestParam int month) {
        Map<String, Object> response = new HashMap<>();
        try {
            int rewardCount = rewardChildService.countMonthlyNotRewarded(user_id, year, month);
            response.put("success", true);
            response.put("rewardCount", rewardCount);
            return ResponseEntity.ok(response);
        } catch (ExecutionException | InterruptedException e) {
            response.put("success", false);
            response.put("message", "보상 계산에 실패했습니다. Error: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }


    // 한 달간 첨삭된 보상 목록을 모두 조회 -> rewarded 가 true
    @GetMapping("/monthly-rewarded-lists")
    public ResponseEntity<Map<String, Object>> listMonthlyRewarded(
            @RequestParam String user_id,
            @RequestParam int year,
            @RequestParam int month) {
        Map<String, Object> response = new HashMap<>();
        try {
            List<RewardChildDTO> rewards = rewardChildService.listMonthlyRewarded(user_id, year, month);

            if (rewards.isEmpty()) {
                response.put("success", false);
                response.put("message", "해당 월에 첨삭된 보상이 없습니다.");
            } else {
                response.put("success", true);
                response.put("rewards", rewards);
            }

            return ResponseEntity.ok(response);
        } catch (ExecutionException | InterruptedException e) {
            response.put("success", false);
            response.put("message", "보상 목록을 가져오는 데 실패했습니다. Error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // 한 달간 아직 첨삭되지 않은 보상 목록을 모두 조회 -> rewarded 가 false
    @GetMapping("/monthly-not-rewarded-lists")
    public ResponseEntity<Map<String, Object>> listMonthlyNotRewarded(
            @RequestParam String user_id,
            @RequestParam int year,
            @RequestParam int month) {
        Map<String, Object> response = new HashMap<>();
        try {
            List<RewardChildDTO> rewards = rewardChildService.listMonthlyNotRewarded(user_id, year, month);

            if (rewards.isEmpty()) {
                response.put("success", false);
                response.put("message", "해당 월에 대한 보상이 모두 첨삭 완료되었습니다.");
            } else {
                response.put("success", true);
                response.put("rewards", rewards);
            }

            return ResponseEntity.ok(response);
        } catch (ExecutionException | InterruptedException e) {
            response.put("success", false);
            response.put("message", "보상 목록을 가져오는 데 실패했습니다. Error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

}
