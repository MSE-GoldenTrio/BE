package com.example.iplan.Service;

import com.example.iplan.DTO.RewardChildDTO;
import com.example.iplan.Domain.PlanChild;
import com.example.iplan.Domain.RewardChild;
import com.example.iplan.ExceptionHandler.CustomException;
import com.example.iplan.Repository.RewardChildRepository;
import com.example.iplan.auth.UserRepository;
import com.example.iplan.auth.Users;
import com.example.iplan.util.AES256Encryptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

@Slf4j
@RequiredArgsConstructor
@Service
public class RewardChildService {

    private final RewardChildRepository rewardChildRepository;
    private final UserRepository userRepository;
    private final AES256Encryptor aes;
    /**
     * 새로운 보상을 저장하는 기능
     * @param rewardDto 저장할 보상 객체의 DTO
     * @return 저장 결과
     * @throws ExecutionException
     * @throws InterruptedException
     */
    public ResponseEntity<Map<String, Object>> saveReward(RewardChildDTO rewardDto, String nickname) throws Exception {
        Map<String, Object> response = new HashMap<>();

        // 사용자 인증
        Users user = userRepository.findByNickname(nickname)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // 빌더 패턴을 사용하여 RewardChild 객체 생성
        RewardChild reward = RewardChild.builder()
                .user_id(nickname)
                .content(aes.encrypt(rewardDto.getContent()))
                .post_date(rewardDto.getPost_date())
                .post_year(rewardDto.getPost_year())
                .post_month(rewardDto.getPost_month())
                .post_day(rewardDto.getPost_day())
                .rewarded(rewardDto.isRewarded())
                .build();

        try {
            rewardChildRepository.saveWithAutoIncrement(reward);
            log.info("Saved successfully!! Reward ID: {}", reward.getId());

            response.put("success", true);
            response.put("message", "보상이 정상적으로 저장되었습니다.");
            response.put("id", reward.getId());
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            throw new CustomException("보상 저장에 실패했습니다. Error: " + e, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 사용자의 단일 보상 반환 -> onSnapshot() 감지로 인한 해당 보상의 데이터 반환
     */
    public Map<String, Object> getRewardById(String childNickname, String rewardId) throws Exception {
        // rewardId에 해당하는 계획 가져오기
        RewardChild reward = rewardChildRepository.findRewardByID(rewardId);
        reward.setContent(aes.decrypt(reward.getContent()));
        try {
            if (reward != null) {
                // 보상의 user_id와 인증객체 유저와 일치한지 확인
                log.info("아이쪽 단일 보상 조회 user_id : " + reward.getUser_id() + ", 요청 사용자 아이디: " + childNickname);
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

    /**
     * 특정 날짜의 보상을 가져오는 기능
     * @param nickname 사용자 ID
     * @param year 해당 연도 (예: 2025)
     * @param month 해당 월 (예: 3)
     * @param day 해당 일 (예: 28)
     * @return 해당 날짜의 보상 객체 (RewardChildDTO)
     * @throws ExecutionException
     * @throws InterruptedException
     */
    public RewardChildDTO getDailyReward(String nickname, int year, int month, int day) throws Exception {
        String formattedDate = String.format("%04d-%02d-%02d", year, month, day); // "2025-03-28" 형식으로 만들기

        RewardChild rewardChild = rewardChildRepository.findRewardChildByDay(nickname, formattedDate);

        if (rewardChild == null) {
            return null;  // 해당 날짜의 보상이 존재하지 않으면 null 반환
        }

        log.info("Daily reward: {}", aes.decrypt(rewardChild.getContent()));

        // DTO로 변환하여 반환
        return RewardChildDTO.builder()
                .id(rewardChild.getId())
                .user_id(aes.decrypt(rewardChild.getUser_id()))
                .content(aes.decrypt(rewardChild.getContent()))
                .post_date(rewardChild.getPost_date())
                .post_year(rewardChild.getPost_year())
                .post_month(rewardChild.getPost_month())
                .post_day(rewardChild.getPost_day())
                .rewarded(rewardChild.isRewarded())
                .success(rewardChild.isSuccess())
                .build();
    }

    /**
     * 사용자의 모든 보상을 조회하는 기능
     * @param nickname 사용자 닉네임 (AuthenticationPrincipal로 인증된 사용자)
     * @return 사용자의 모든 보상 목록 (RewardChildDTO 리스트)
     * @throws ExecutionException
     * @throws InterruptedException
     */
    public List<RewardChildDTO> getAllRewards(String nickname) throws Exception {
        // 사용자가 존재하는지 확인
        Users user = userRepository.findByNickname(nickname)
                .orElseThrow(() -> new CustomException("사용자를 찾을 수 없습니다.", HttpStatus.NOT_FOUND));
        log.info("Get all rewards start!!");
        try {
            // 사용자의 모든 보상을 가져오기
            List<RewardChildDTO> rewards = rewardChildRepository.findRewardChildDtoByUserId(nickname);

            for(RewardChildDTO dto : rewards){
                dto.setUser_id(aes.decrypt(dto.getUser_id()));
                dto.setContent(aes.decrypt(dto.getContent()));
            }

            if (rewards.isEmpty()) {
                log.info("{}'s reward is not exist.", aes.decrypt(nickname));
            } else {
                log.info("{}(복호화 됨)'s rewards received successfully!! Size: {}", aes.decrypt(nickname), rewards.size());
            }

            return rewards;
        } catch (Exception e) {
            log.error("Error of {}(복호화 됨): {}", aes.decrypt(nickname), e.getMessage());
            throw new ExecutionException("보상 목록을 가져오는 중 오류가 발생했습니다.", e);
        }
    }

    /**
     * 보상을 ID로 삭제하는 기능
     * @param documentID 삭제할 보상의 ID
     * @return 삭제 결과
     * @throws ExecutionException
     * @throws InterruptedException
     */
    public ResponseEntity<Map<String, Object>> deleteReward(String documentID) throws ExecutionException, InterruptedException {
        Map<String, Object> response = new HashMap<>();

        try {
            // 해당 ID의 보상을 조회
            RewardChild reward = rewardChildRepository.findEntityByDocumentId(documentID);
            if (reward == null) {
                throw new CustomException("해당 ID의 보상을 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
            }

            // 보상이 이미 지급된 경우 (is_rewarded 가 true) 삭제를 허용하지 않음
            if (reward.isRewarded()) {
                throw new CustomException("해당 보상은 이미 지급되어 삭제할 수 없습니다.", HttpStatus.FORBIDDEN);
            }

            // 지급되지 않은 보상만 삭제 허용
            RewardChild rewardChild = rewardChildRepository.findEntityByDocumentId(documentID);
            rewardChildRepository.delete(rewardChild);
            response.put("success", true);
            response.put("message", "보상이 정상적으로 삭제되었습니다.");
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            throw new CustomException("보상 삭제에 실패했습니다. Error: " + e, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }


    /**
     * 기존 보상을 수정하는 기능
     * @param rewardDto 수정할 보상 객체의 DTO
     * @return 수정 결과
     * @throws ExecutionException
     * @throws InterruptedException
     */
    public ResponseEntity<Map<String, Object>> updateReward(RewardChildDTO rewardDto, String nickname) throws Exception {
        Map<String, Object> response = new HashMap<>();

        RewardChild existingReward = rewardChildRepository.findEntityByDocumentId(rewardDto.getId());

        if (existingReward == null) {
            throw new CustomException("해당 ID의 보상을 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
        }

        // 날짜 문자열에서 yyyy-MM-dd만 추출
        String dateOnly = rewardDto.getPost_date().substring(0, 10); // "2025-03-27"

        // 해당 날짜의 보상 조회
        RewardChild rewardChildOnSameDay = rewardChildRepository.findRewardChildByDay(nickname, dateOnly);

        // 보상 데이터 조회 결과 확인
        if (rewardChildOnSameDay == null) {
            log.info("rewardChildOnSameDay is null. No reward found for date: {}", dateOnly);
        } else {
            log.info("rewardChildOnSameDay found. isRewarded: {}", rewardChildOnSameDay.isRewarded());
        }

        // 만약 해당 날짜의 보상이 이미 지급된 상태라면 수정 금지
        if (rewardChildOnSameDay != null && rewardChildOnSameDay.isRewarded()) {
            log.info("Error: reward is already rewarded");
            throw new CustomException("해당 날짜의 보상이 이미 지급되어 수정할 수 없습니다.", HttpStatus.FORBIDDEN);
        }

        RewardChild updatedReward = RewardChild.builder()
                .id(existingReward.getId())
                .user_id(rewardDto.getUser_id() != null ? rewardDto.getUser_id() : existingReward.getUser_id())
                .content(rewardDto.getContent() != null ? aes.encrypt(rewardDto.getContent()) : existingReward.getContent())
                .post_date(rewardDto.getPost_date() != null ? rewardDto.getPost_date() : existingReward.getPost_date())
                .post_year(rewardDto.getPost_date() != null ? rewardDto.getPost_year() : existingReward.getPost_year())
                .post_month(rewardDto.getPost_date() != null ? rewardDto.getPost_month() : existingReward.getPost_month())
                .post_day(rewardDto.getPost_date() != null ? rewardDto.getPost_day() : existingReward.getPost_day())
                .build();

        try {
            rewardChildRepository.update(updatedReward);
            response.put("success", true);
            response.put("message", "보상이 정상적으로 수정되었습니다.");
            log.info("Reward updated successfully!!");
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            throw new CustomException("보상 수정에 실패했습니다. Error: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

}
