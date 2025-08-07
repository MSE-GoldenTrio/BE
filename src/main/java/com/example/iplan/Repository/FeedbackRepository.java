package com.example.iplan.Repository;

import com.example.iplan.Domain.Feedback;
import com.example.iplan.ExceptionHandler.CustomException;
import com.example.iplan.Repository.DefaultFirebaseRepository.DefaultFirebaseDBRepository;
import com.google.cloud.firestore.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;

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
            throw new CustomException("일시적 오류가 발생하였습니다.","해당 ID:" + feedbackId + "의 Feedback 문서가 없습니다.", HttpStatus.NOT_FOUND);
        }
        return feedback;
    }
}
