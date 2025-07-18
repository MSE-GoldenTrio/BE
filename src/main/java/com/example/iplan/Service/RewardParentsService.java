package com.example.iplan.Service;

import com.example.iplan.DTO.RewardChildDTO;
import com.example.iplan.ExceptionHandler.CustomException;
import com.example.iplan.Repository.RewardChildRepository;
import com.example.iplan.auth.UserRepository;
import com.example.iplan.auth.Users;
import com.example.iplan.util.AES256Encryptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Slf4j
@RequiredArgsConstructor
@Service
public class RewardParentsService {

    private final RewardChildRepository rewardChildRepository;
    private final UserRepository userRepository;
    private final AES256Encryptor aes;

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

            for(RewardChildDTO dto : rewards){
                dto.setContent(aes.decrypt(dto.getContent()));
            }

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

}
