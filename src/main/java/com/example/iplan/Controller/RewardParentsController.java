package com.example.iplan.Controller;

import com.example.iplan.DTO.RewardParentsDTO;
import com.example.iplan.Domain.RewardParents;
import com.example.iplan.Service.RewardParentsService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.ExecutionException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/reward-parents")
public class RewardParentsController {

    private final RewardParentsService rewardParentsService;

    // 아이들이 설정한 보상 지급
    @PostMapping("/{childRewardId}")
    public ResponseEntity<Map<String, Object>> addRewardParents(@RequestBody RewardParentsDTO rewardParents, @PathVariable String childRewardId) throws ExecutionException, InterruptedException {
        return rewardParentsService.saveRewardParents(rewardParents, childRewardId);
    }

    // RewardParents 에서 해당 문서의 id에 대한 세부사항 가져옴
    @GetMapping("/{parentsRewardId}")
    public ResponseEntity<RewardParents> getRewardParents(@PathVariable String parentsRewardId) throws ExecutionException, InterruptedException {
        RewardParents rewardParents = rewardParentsService.getRewardParents(parentsRewardId);
        return ResponseEntity.ok(rewardParents);
    }

    @PatchMapping
    public ResponseEntity<Map<String, Object>> updateRewardParents(@RequestBody RewardParentsDTO rewardParents) throws ExecutionException, InterruptedException {
        return rewardParentsService.updateRewardParents(rewardParents);
    }
}
