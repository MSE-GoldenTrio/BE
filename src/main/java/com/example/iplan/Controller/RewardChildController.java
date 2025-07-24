package com.example.iplan.Controller;

import com.example.iplan.DTO.RewardChildDTO;
import com.example.iplan.Domain.RewardChild;
import com.example.iplan.Service.RewardChildService;
import com.example.iplan.auth.oauth2.CustomOAuth2UserDetails;
import com.google.firebase.database.annotations.NotNull;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@RequiredArgsConstructor
@RestController
@RequestMapping("/child/reward")
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
    public ResponseEntity<Map<String, Object>> saveReward(@RequestBody @NotNull RewardChildDTO rewardDto, @AuthenticationPrincipal CustomOAuth2UserDetails user) throws ExecutionException, InterruptedException {

        String childNickname = user.getUsername();
        log.info("Received RewardChildDTO: {}, AuthenticationPrincipal email: {}", rewardDto, childNickname);

        return rewardChildService.saveReward(rewardDto, childNickname);
    }

    /**
     * 사용자의 단일 보상 목록 반환
     * -> 프론트에서 onSnapshot()으로 데이터 변경 감지 후 바뀐 보상만 가져오는것
     */
    @Operation(summary = "onSnapshot()으로 감지한 바뀐 보상 데이터 GET", description = "해당 사용자의 단일 보상 목록을 가져온다.")
    @GetMapping("/changed")
    public ResponseEntity<Map<String, Object>> getChangedReward(@AuthenticationPrincipal CustomOAuth2UserDetails user, @RequestParam("rewardId") String rewardId) throws ExecutionException, InterruptedException {
        String nickname = user.getUsername();
        log.info("변경된 보상 ID: {}", rewardId);
        Map<String, Object> response = rewardChildService.getRewardById(nickname, rewardId);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    /**
     * 특정 날짜의 보상을 가져옴
     * @param year 해당 연도
     * @param month 해당 월 (1월은 1, 12월은 12)
     * @param day 해당 일 (01, 02, ..., 31)
     * @return 해당 날짜의 보상 정보
     */
    @Operation(summary = "특정 날짜의 보상 GET", description = "특정 날짜의 보상을 가져온다.")
    @GetMapping("/daily")
    public ResponseEntity<Map<String, Object>> getDailyReward(
            @RequestParam int year,
            @RequestParam int month,
            @RequestParam int day,
            @AuthenticationPrincipal CustomOAuth2UserDetails user) {

        Map<String, Object> response = new HashMap<>();
        try {
            String nickname = user.getUsername();
            RewardChildDTO reward = rewardChildService.getDailyReward(nickname, year, month, day);

            if (reward != null) {
                response.put("success", true);
                response.put("reward", reward);
                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("message", "해당 날짜에 대한 보상이 존재하지 않습니다.");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
        } catch (ExecutionException | InterruptedException e) {
            response.put("success", false);
            response.put("message", "보상 데이터를 가져오는 데 실패했습니다. Error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * 사용자의 모든 보상을 조회
     * @param user
     * @return 모든 보상 목록
     */
    @Operation(summary = "모든 보상 목록 GET", description = "해당 사용자의 모든 보상 목록을 조회한다.")
    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> getAllRewards(@AuthenticationPrincipal CustomOAuth2UserDetails user) {
        Map<String, Object> response = new HashMap<>();
        try {
            String nickname = user.getUsername();
            List<RewardChildDTO> rewards = rewardChildService.getAllRewards(nickname);

            if (rewards.isEmpty()) {
                response.put("success", false);
                response.put("message", "보상 목록이 존재하지 않습니다.");
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


    /**
     * 보상을 수정
     * @param rewardDto 수정할 Reward 객체
     * @return 성공 여부 및 오류 메시지
     * @throws ExecutionException
     * @throws InterruptedException
     */
    @Operation(summary = "보상 수정 UPDATE", description = "보상 내용을 수정한다.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = {
                            @Content(schema = @Schema(implementation = RewardChildDTO.class))
                    }))
    @PatchMapping("/update")
    public ResponseEntity<Map<String, Object>> updateReward(@RequestBody @NotNull RewardChildDTO rewardDto, @AuthenticationPrincipal CustomOAuth2UserDetails user) throws ExecutionException, InterruptedException {
        String childNickname = user.getUsername();
        log.info("Received RewardChildDTO for update reward: {}, AuthenticationPrincipal email: {}", rewardDto, childNickname);
        return rewardChildService.updateReward(rewardDto, childNickname);
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
    public ResponseEntity<Map<String, Object>> deleteReward(@PathVariable @Parameter(example = "slidfjil123") String documentID) throws ExecutionException, InterruptedException {
        return rewardChildService.deleteReward(documentID);
    }


}
