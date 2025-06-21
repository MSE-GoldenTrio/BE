package com.example.iplan.Repository;

import com.example.iplan.Domain.Feedback;
import com.example.iplan.Repository.DefaultFirebaseRepository.DefaultFirebaseDBRepository;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Repository
public class FeedbackRepository extends DefaultFirebaseDBRepository<Feedback>  {

    public FeedbackRepository(Firestore firestore) {
        super(firestore);
        setEntityClass(Feedback.class);
        setCollectionName("Feedback"); // Firestore에서 저장할 컬렉션 이름 설정
    }

    /**
     * 특정 사용자 ID와 일치하는 보상 부모 목록을 반환
     */
    public List<Feedback> findRewardParentsListByUserId(String userId) throws ExecutionException, InterruptedException {
        CollectionReference collection = firestore.collection("Feedback");

        ApiFuture<QuerySnapshot> apiFutureList = collection
                .whereEqualTo("user_id", userId)  // 필드 이름을 정확히 설정해야 함
                .get();

        QuerySnapshot querySnapshot = apiFutureList.get();

        List<Feedback> rewardParents = new ArrayList<>();

        for (QueryDocumentSnapshot document : querySnapshot.getDocuments()) {
            rewardParents.add(document.toObject(Feedback.class));
        }

        return rewardParents;
    }

    /**
     * 특정 보상 ID와 일치하는 보상 목록을 반환
     */
    public List<Feedback> findByRewardId(String user_id, String reward_id) throws ExecutionException, InterruptedException {
        Map<String, Object> filters = Map.of(
                "user_id", user_id,
                "reward_id", reward_id
        );
        return findAllByFields(filters);
    }

    /**
     * 특정 보상 ID와 일치하는 보상 목록을 반환
     */
    public List<Feedback> findByUserIdChildId(String parentNickname, String childNickname) throws ExecutionException, InterruptedException {
        Map<String, Object> filters = Map.of(
                "user_id", parentNickname,
                "child_id", childNickname
        );
        return findAllByFields(filters);
    }
}
