package com.example.iplan.Service;

import com.example.iplan.DTO.FeedbackDTO;
import com.example.iplan.Domain.Feedback;
import com.example.iplan.Domain.RewardChild;
import com.example.iplan.ExceptionHandler.CustomException;
import com.example.iplan.Repository.FeedbackRepository;
import com.example.iplan.Repository.RewardChildRepository;
import com.example.iplan.auth.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ExecutionException;

@Slf4j
@RequiredArgsConstructor
@Service
public class FeedbackService {
    private final FeedbackRepository feedbackRepository;
    private final RewardChildRepository rewardChildRepository;
    private final UserRepository userRepository;

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

            // 2. 빌더 패턴을 사용하여 Feedback 객체 생성 및 설정
            Feedback newFeedback = Feedback.builder()
                    .user_id(parentNickname)
                    .child_id(childId)
                    .reward_id(feedbackDTO.getReward_id())
                    .comment(feedbackDTO.getComment())
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
     * 부모의 모든 피드백 목록 반환
     */
    public ResponseEntity<Map<String, Object>> getAllFeedbacks(String parentNickname) throws ExecutionException, InterruptedException {
        Map<String, Object> response = new HashMap<>();
        try {
            // 사용자의 모든 피드백을 가져오기
            List<Feedback> feedbacks = feedbackRepository.findFeedbackDtoByUserId(parentNickname);

            if (feedbacks.isEmpty()) {
                response.put("success", false);
                response.put("message", "피드백 목록이 존재하지 않습니다.");
                log.info("피드백 목록이 존재하지 않습니다.: {}", parentNickname);
            } else {
                response.put("success", true);
                response.put("feedbacks", feedbacks);
                log.info("{}'s feedbacks received successfully!! Size: {}", parentNickname, feedbacks.size());
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error of {}: {}", parentNickname, e.getMessage());
            throw new ExecutionException("보상 목록을 가져오는 중 오류가 발생했습니다.", e);
        }
    }


    /**
     * 부모의 해당 아이에 대한 모든 코멘트와 별점을 조회하는 기능
     */
    public Map<String, Object> getOneChildFeedback(String childNickname, String parentNickname) throws ExecutionException, InterruptedException {
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
     * 기존 피드백을 수정하는 기능 (댓글, 별점, 성공 여부 포함)
     * @param feedbackDTO 수정할 RewardParents 객체
     * @return 수정 결과
     * @throws ExecutionException
     * @throws InterruptedException
     */
    public ResponseEntity<Map<String, Object>> updateFeedback(FeedbackDTO feedbackDTO, String parentNickname) throws ExecutionException, InterruptedException {
        Map<String, Object> response = new HashMap<>();

        try {
            // 1. reward_id로 Feedback 에서 해당 객체 찾기
            Feedback existingFeedback = feedbackRepository.findByField("reward_id", feedbackDTO.getReward_id());

            if (existingFeedback == null) {
                throw new CustomException("해당 ID의 지급된 피드백을 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
            }
            if (!Objects.equals(existingFeedback.getUser_id(), parentNickname)) {
                throw new CustomException("해당 피드백에 대한 수정 권한이 없습니다.", HttpStatus.UNAUTHORIZED);
            }

            // 빌더 패턴을 사용하여 Feedback 객체를 새롭게 업데이트
            Feedback updateFeedback = Feedback.builder()
                    .id(existingFeedback.getId()) // 기존 ID 유지
                    .user_id(existingFeedback.getUser_id())
                    .child_id(existingFeedback.getChild_id())
                    .reward_id(existingFeedback.getReward_id())
                    .comment(feedbackDTO.getComment() != null ? feedbackDTO.getComment() : existingFeedback.getComment())
                    .grade(feedbackDTO.getGrade() != 0 ? feedbackDTO.getGrade() : existingFeedback.getGrade())
                    .rewarded(true) // 항상 보상 지급 상태를 true로 설정
                    .success(feedbackDTO.isSuccess())  // 보상을 회수하는 경우도 가능
                    .build();


            feedbackRepository.update(updateFeedback);

            // 보상 객체도 함께 수정
            RewardChild reward = rewardChildRepository.findEntityByDocumentId(existingFeedback.getReward_id());
            if (reward == null) {
                throw new CustomException("해당 ID의 보상을 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
            }
            reward.setRewarded(true);
            reward.setSuccess(feedbackDTO.isSuccess());
            rewardChildRepository.update(reward);

            response.put("success", true);
            response.put("message", "부모님의 코멘트와 별점이 정상적으로 수정되었습니다.");
            response.put("id", updateFeedback.getId());
            log.info("피드백 수정 완료 id: {}", updateFeedback.getId());
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            throw new CustomException("수정에 실패했습니다. Error: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 단일 피드백 반환
     */
    public Map<String, Object> getFeedbackById(String childNickname, String feedbackId) throws ExecutionException, InterruptedException {
        // feedbackId에 해당하는 계획 가져오기
        Feedback feedback = feedbackRepository.findFeedbackByID(feedbackId);

        try {
            if (feedback != null) {
                // 피드백의 user_id와 인증객체 유저와 일치한지 확인
                if (!Objects.equals(feedback.getChild_id(), childNickname)) {
                    throw new CustomException("해당 피드백에 대한 접근 권한이 없습니다.", HttpStatus.FORBIDDEN);  // 404 에러 반환
                }
                return Map.of("success", true, "message", feedbackId + " 피드백 반환 성공", "feedback", feedback);
            } else {
                throw new CustomException(feedbackId + " ID의 피드백이 존재하지 않음", HttpStatus.NOT_FOUND);
            }
        } catch (Exception e) {
            throw new CustomException("서버 오류 발생: " + e, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }


}
