package com.example.iplan.Service;

import com.example.iplan.DTO.RewardChildDTO;
import com.example.iplan.DTO.FeedbackDTO;
import com.example.iplan.Domain.Feedback;
import com.example.iplan.Domain.PlanChild;
import com.example.iplan.Domain.RewardChild;
import com.example.iplan.Domain.RewardParents;
import com.example.iplan.ExceptionHandler.CustomException;
import com.example.iplan.Repository.FeedbackRepository;
import com.example.iplan.Repository.RewardChildRepository;
import com.example.iplan.util.AES256Encryptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@Slf4j
@RequiredArgsConstructor
@Service
public class FeedbackService {
    private final FeedbackRepository feedbackRepository;
    private final RewardChildRepository rewardChildRepository;
    private final AES256Encryptor aes;

    /**
     * 부모님의 보상 코멘트와 별점, 보상 지급 여부를 저장하는 기능
     * 1) 보상을 지급 or 2) 보상을 보류
     */
    public ResponseEntity<Map<String, Object>> saveFeedback(FeedbackDTO feedbackDTO, String parentNickname)
            throws ExecutionException, InterruptedException {

        log.info("저장할 피드백: {}", feedbackDTO);
        Map<String, Object> response = new HashMap<>();

        try {
            String childId = feedbackDTO.getChild_id();

            // 1. rewardID로 RewardChild 엔티티에서 해당 보상 검색
            RewardChild reward = rewardChildRepository.findEntityByDocumentId(feedbackDTO.getReward_id());
            if (reward == null) {
                throw new CustomException("해당 ID의 보상을 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
            }

            String encryptedComment = aes.encrypt(feedbackDTO.getComment());

            // 2. 빌더 패턴을 사용하여 Feedback 객체 생성 및 설정
            Feedback newFeedback = Feedback.builder()
                    .user_id(parentNickname)
                    .child_id(childId)
                    .reward_id(feedbackDTO.getReward_id())
                    .comment(encryptedComment)
                    .grade(feedbackDTO.getGrade())
                    .rewarded(true) // 첨삭 여부 true
                    .success(feedbackDTO.isSuccess())
                    .build();

            // 3. Feedback 저장
            feedbackRepository.saveWithAutoIncrement(newFeedback);

            // 4. RewardChild의 보상 지급 상태를 업데이트하고 저장
            reward.setRewarded(true);
            reward.setSuccess(feedbackDTO.isSuccess());
            rewardChildRepository.update(reward);

            response.put("success", true);
            response.put("message", "부모님의 코멘트와 별점이 정상적으로 저장되었고, 보상 지급 상태가 업데이트되었습니다.");
            response.put("id", newFeedback.getId());
            log.info("피드백 저장 완료 id: {}", newFeedback.getId());

            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            throw new CustomException("저장에 실패했습니다. Error: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 부모의 해당 아이에 대한 모든 코멘트와 별점을 조회하는 기능
     */
    public Map<String, Object> getFeedbackParents(String childNickname, String parentNickname) throws ExecutionException, InterruptedException {
        Map<String, Object> response = new HashMap<>();
        try {
            List<Feedback> feedbacks = feedbackRepository.findByUserIdChildId(parentNickname, childNickname);

            if (feedbacks == null || feedbacks.isEmpty()) {
                response.put("success", false);
                response.put("message", "등록된 피드백이 없습니다.");
            } else {
                // 문서 ID를 Feedback 객체에 설정
                for (Feedback feedback : feedbacks) {
                    if (feedback.getId() == null) {
                        feedback.setId(UUID.randomUUID().toString()); // ID가 없으면 임시 ID 부여
                        feedback.setComment(aes.decrypt(feedback.getComment()));
                    }
                }
                response.put("success", true);
                response.put("message", "아이의 모든 피드백을 조회 완료하였습니다.");
                response.put("feedbacks", feedbacks);
            }
            log.info("Get feedbacks successfully!");
            return response;
        } catch (InterruptedException e) {
            throw new InterruptedException("서버 오류");
        } catch (Exception e) {
            throw new ExecutionException("피드백 조회에 실패했습니다. Error: " + e.getMessage(), e);
        }
    }

    /**
     * 부모님의 보상 코멘트와 별점 수정 기능
     * @param rewardParentsDTO 수정할 RewardParents 객체
     * @return 수정 결과
     * @throws ExecutionException
     * @throws InterruptedException
     */
    public ResponseEntity<Map<String, Object>> updateRewardParents(FeedbackDTO rewardParentsDTO) throws ExecutionException, InterruptedException {
        Map<String, Object> response = new HashMap<>();

        try {
            // 1. reward_id로 Feedback 에서 해당 객체 찾기
            Feedback existingFeedback = feedbackRepository.findByField("reward_id", rewardParentsDTO.getReward_id());

            if (existingFeedback == null) {
                throw new CustomException("해당 ID의 지급된 피드백을 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
            }

            // 빌더 패턴을 사용하여 Feedback 객체를 새롭게 업데이트
            Feedback updatedRewardParents = Feedback.builder()
                    .id(existingFeedback.getId()) // 기존 ID 유지
                    .user_id(existingFeedback.getUser_id())
                    .reward_id(existingFeedback.getReward_id())
                    .comment(rewardParentsDTO.getComment() != null ? aes.encrypt(rewardParentsDTO.getComment()) : existingFeedback.getComment())
                    .grade(rewardParentsDTO.getGrade() != 0 ? rewardParentsDTO.getGrade() : existingFeedback.getGrade())
                    .rewarded(true) // 항상 보상 지급 상태를 true로 설정
                    .success(rewardParentsDTO.isSuccess())  // 보상을 회수하는 경우도 가능
                    .build();


            feedbackRepository.update(updatedRewardParents);

            String rewardId = existingFeedback.getReward_id();
            RewardChild reward = rewardChildRepository.findEntityByDocumentId(rewardId);
            if (reward == null) {
                throw new CustomException("해당 ID의 보상을 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
            }
            reward.setRewarded(true);
            reward.setSuccess(rewardParentsDTO.isSuccess());
            rewardChildRepository.update(reward);

            response.put("success", true);
            response.put("message", "부모님의 코멘트와 별점이 정상적으로 수정되었습니다.");
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            throw new CustomException("수정에 실패했습니다. Error: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

}
