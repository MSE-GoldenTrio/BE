package com.example.iplan.Repository;

import com.example.iplan.Domain.ScreenTimeOCRResult;
import com.example.iplan.Repository.DefaultFirebaseRepository.DefaultFirebaseDBRepository;
import com.google.cloud.firestore.Firestore;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.concurrent.ExecutionException;

@Repository
public class GetScreenTimeOCRRepository extends DefaultFirebaseDBRepository<ScreenTimeOCRResult> {

    public GetScreenTimeOCRRepository(Firestore firestore){
        super(firestore);
        setEntityClass(ScreenTimeOCRResult.class);
        setCollectionName("ScreenTimeOCRResult");
    }

    public ScreenTimeOCRResult findByDate(String user_id, String targetDate) throws ExecutionException, InterruptedException {
        Map<String, Object> filters = Map.of(
                "user_id", user_id,
                "date", targetDate
        );
        return findByFields(filters);
    }
}
