package com.example.iplan.Service;

import com.example.iplan.DTO.RewardChildDTO;
import com.example.iplan.Domain.PlanChild;
import com.example.iplan.Domain.RewardChild;
import com.example.iplan.ExceptionHandler.CustomException;
import com.example.iplan.Repository.RewardChildRepository;
import com.example.iplan.auth.UserRepository;
import com.example.iplan.auth.Users;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

@Slf4j
@RequiredArgsConstructor
@Service
public class RewardParentsService {

    private final RewardChildRepository rewardChildRepository;
    private final UserRepository userRepository;

    /**
     * 여러 자녀 닉네임을 기반으로 모든 자녀의 보상 목록 반환
     */
    public Map<String, Object> getRewardsForAllChildren(List<String> childNicknames) throws ExecutionException, InterruptedException {
        Map<String, Object> allRewards = new HashMap<>();

        for (String childNickname : childNicknames) {
            Map<String, Object> childRewards = getAllRewardsByNickname(childNickname);
            allRewards.put(childNickname, childRewards);
        }

        return allRewards;
    }

    /**
     * 단일 자녀 닉네임을 기반으로 보상 목록 반환
     */
    public Map<String, Object> getAllRewardsByNickname(String nickname) throws ExecutionException, InterruptedException {
        Map<String, Object> response = new HashMap<>();

        // 사용자 존재 여부 확인
        Users user = userRepository.findByNickname(nickname)
                .orElseThrow(() -> new CustomException("사용자를 찾을 수 없습니다: " + nickname, HttpStatus.NOT_FOUND));

        try {
            List<RewardChildDTO> rewards = rewardChildRepository.findRewardChildDtoByUserId(nickname);

            if (rewards == null || rewards.isEmpty()) {
                response.put("success", false);
                response.put("message", "등록된 보상이 없습니다.");
            } else {
                response.put("success", true);
                response.put("rewards", rewards);
            }

            log.info("Get reward for {} successfully!", nickname);
            return response;

        } catch (Exception e) {
            log.error("Error while getting rewards for {}: {}", nickname, e.getMessage());
            throw new ExecutionException("보상 조회 중 오류 발생", e);
        }
    }

    /**
     * 한 자녀의 단일 보상 반환 -> onSnapshot() 감지로 인한 해당 계획의 데이터 반환
     */
    public Map<String, Object> getRewardById(String childNickname, String rewardId) throws ExecutionException, InterruptedException {
        // planId에 해당하는 계획 가져오기
        RewardChild reward = rewardChildRepository.findRewardByID(rewardId);

        try {
            if (reward != null) {
                // 보상의 user_id와 인증객체 유저와 일치한지 확인
                if (!Objects.equals(reward.getUser_id(), childNickname)) {
                    throw new CustomException("해당 보상에 대한 접근 권한이 없습니다.", HttpStatus.FORBIDDEN);  // 404 에러 반환
                }
                return Map.of("success", true, "message", rewardId + " 보상 반환 성공", "reward", reward);
            } else {
                throw new CustomException(rewardId + " ID의 보상이 존재하지 않음", HttpStatus.NOT_FOUND);
            }
        } catch (Exception e) {
            throw new CustomException("서버 오류 발생: " + e, HttpStatus.INTERNAL_SERVER_ERROR);
        }


    }

}
