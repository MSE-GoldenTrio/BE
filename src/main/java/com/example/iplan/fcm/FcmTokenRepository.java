package com.example.iplan.fcm;

import com.example.iplan.Repository.DefaultFirebaseRepository.DefaultFirebaseDBRepository;
import com.google.cloud.firestore.Firestore;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Repository
public class FcmTokenRepository extends DefaultFirebaseDBRepository<FcmToken> {
    public FcmTokenRepository(Firestore firestore) {
        super(firestore);
        setEntityClass(FcmToken.class);
        setCollectionName("FcmToken");
    }

    /**
     * 유저 닉네임과 fcmToken 으로 검색하여 단일 문서 반환
     */
    public FcmToken findByHashedUserIdAndToken(String hashedUserId, String fcmToken) throws ExecutionException, InterruptedException {
        Map<String, Object> filters = Map.of(
                "user_id", hashedUserId,
                "token", fcmToken
        );
        return findByFields(filters);
    }

    /**
     * 유저 닉네임으로 모든 문서 반환
     */
    public List<FcmToken> findByHashedUserId(String hashedUserId) throws ExecutionException, InterruptedException {
        return findEntityAll(hashedUserId);
    }
}
