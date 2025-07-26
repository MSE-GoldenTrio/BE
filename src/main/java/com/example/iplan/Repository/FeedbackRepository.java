package com.example.iplan.Repository;

import com.example.iplan.DTO.FeedbackDTO;
import com.example.iplan.Domain.Feedback;
import com.example.iplan.Domain.RewardChild;
import com.example.iplan.ExceptionHandler.CustomException;
import com.example.iplan.Repository.DefaultFirebaseRepository.DefaultFirebaseDBRepository;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.google.firebase.cloud.FirestoreClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Repository
public class FeedbackRepository extends DefaultFirebaseDBRepository<Feedback> {

    public FeedbackRepository(Firestore firestore) {
        super(firestore);
        setEntityClass(Feedback.class);
        setCollectionName("Feedback");
    }

    /**
     * 특정 사용자 ID와 일치하는 피드백 목록을 DTO 형태로 반환
     */
    public List<Feedback> findFeedbackDtoByUserId(String userId) throws ExecutionException, InterruptedException {
        return findAllByField("user_id", userId);
    }

    /**
     * 특정 보상 ID와 사용자 ID에 일치하는 피드백 목록 반환
     */
    public List<Feedback> findByRewardId(String userId, String rewardId) throws ExecutionException, InterruptedException {
        return findAllByFields(Map.of(
                "user_id", userId,
                "reward_id", rewardId
        ));
    }

    /**
     * 특정 부모 닉네임(user_id)과 자녀 닉네임(child_id)에 해당하는 피드백 목록 반환
     */
    public List<Feedback> findByUserIdChildId(String parentNickname, String childNickname) throws ExecutionException, InterruptedException {
        return findAllByFields(Map.of(
                "user_id", parentNickname,
                "child_id", childNickname
        ));
    }

    /**
     * Feedback ID로 해당하는 문서 반환
     */
    public Feedback findFeedbackByID(String feedbackId) throws ExecutionException, InterruptedException {
        Feedback feedback = findEntityByDocumentId(feedbackId);
        if (feedback == null) {
            throw new CustomException("해당 ID의 Feedback 문서가 없습니다.", HttpStatus.NOT_FOUND);
        }
        return feedback;
    }

    /**
     * Feedback 리스트를 FeedbackDTO 리스트로 변환
     */
    private List<FeedbackDTO> convertToDTOList(List<Feedback> feedbackList) {
        List<FeedbackDTO> feedbackDTOList = new ArrayList<>();
        for (Feedback feedback : feedbackList) {
            feedbackDTOList.add(convertToDTO(feedback));
        }
        return feedbackDTOList;
    }

    /**
     * Feedback 엔티티를 FeedbackDTO로 변환
     */
    private FeedbackDTO convertToDTO(Feedback feedback) {
        return FeedbackDTO.builder()
                .reward_id(feedback.getReward_id())
                .child_id(feedback.getChild_id())
                .comment(feedback.getComment())
                .grade(feedback.getGrade())
                .success(feedback.isSuccess())
                .build();
    }
}
