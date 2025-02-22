package com.example.iplan.Controller;

import com.example.iplan.DTO.PlanChildDTO;
import com.example.iplan.DTO.RewardChildDTO;
import com.example.iplan.Domain.RewardChild;
import com.example.iplan.Service.RewardChildService;
import com.example.iplan.auth.oauth2.CustomOAuth2UserDetails;
import com.google.firebase.database.annotations.NotNull;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Slf4j
@RequiredArgsConstructor
@Controller
@RequestMapping("/reward-child")
@Tag(name = "아이들 보상 관리 컨트롤러", description = "보상 추가, 삭제, 한 달간의 총 개수 등을 처리한다.")
public class RewardChildController {

    private final RewardChildService rewardChildService;

    /**
     * 보상을 추가(저장)
     * @param rewardDto Reward 객체
     * @return 성공 여부 및 오류 메시지
     * @throws ExecutionException
     * @throws InterruptedException
     */
    @Operation(summary = "보상 추가 POST", description = "받고 싶은 보상을 입력(추가)한다.",
    requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = {
                    @Content(schema = @Schema(implementation = RewardChildDTO.class))
            }))
    @PostMapping
    @ResponseBody
    public ResponseEntity<Map<String, Object>> saveReward(@RequestBody @NotNull RewardChildDTO rewardDto, @AuthenticationPrincipal String nickname) throws ExecutionException, InterruptedException {
        log.info("Received RewardChildDTO: {}, AuthenticationPrincipal email: {}", rewardDto, nickname);

        return rewardChildService.saveReward(rewardDto, nickname);
    }

    /**
     * 보상 세부사항을 가져옴
     * @param documentId 보상 ID
     * @return 보상 객체
     * @throws ExecutionException
     * @throws InterruptedException
     */
    @Operation(summary = "보상 엔티티 GET", description = "해당 ID의 보상 엔티티를 가져온다.",
            parameters = {
                    @Parameter(name = "documentID", description = "해당 보상 문서 Id", example = "xicv3412zz", required = true)
            })
    @GetMapping("/{documentId}")
    @ResponseBody
    public ResponseEntity<RewardChild> getReward(@PathVariable @Parameter(example = "sdfg123") String documentId) throws ExecutionException, InterruptedException {
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
    @Operation(summary = "보상 수정 UPDATE", description = "보상 내용을 수정한다.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = {
                            @Content(schema = @Schema(implementation = RewardChildDTO.class))
                    }))
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
    @Operation(summary = "보상 엔티티 DELETE", description = "해당 ID의 보상 엔티티를 삭제한다.",
            parameters = {
                    @Parameter(name = "documentID", description = "해당 보상 문서 Id", example = "xicv3412zz", required = true)
            })
    @DeleteMapping("/{documentID}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteReward(@PathVariable @Parameter(example = "slidfjil123") String documentID) throws ExecutionException, InterruptedException {
        return rewardChildService.deleteReward(documentID);
    }

    /**
     * 한 달간 작성한 총 보상의 개수
     * @param user_id 사용자 ID
     * @param year 해당 연도
     * @param month 해당 월 (1월은 1, 12월은 12)
     * @return 한 달간 작성한 총 보상의 개수
     */
    @Operation(summary = "한 달간 보상 개수 GET", description = "한 달간 작성한 총 보상 개수를 가져온다.")
    @GetMapping("/monthly-total-rewards-count")
    public ResponseEntity<Map<String, Object>> countMonthlyTotalRewarded(
            @Parameter @RequestParam(name = "user_id") String user_id,
            @Parameter @RequestParam(name = "year") int year,
            @Parameter @RequestParam(name = "month") int month) {
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
