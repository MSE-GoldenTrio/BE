package com.example.iplan.Repository;

import com.example.iplan.Domain.Alarm;
import com.example.iplan.Repository.DefaultFirebaseRepository.DefaultFirebaseDBRepository;
import com.google.cloud.firestore.Firestore;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Repository
public class AlarmRepository extends DefaultFirebaseDBRepository<Alarm> {

    public AlarmRepository(Firestore firestore) {
        super(firestore);
        setEntityClass(Alarm.class);
        setCollectionName("Alarm");
    }

    // 모든 알람 문서를 조회
    public List<Alarm> getAllAlarms() throws ExecutionException, InterruptedException {
        return findAll();
    }

    // Plan Id와 FcmToken 으로 단일 문서 반환
    public Alarm getAlarmByPlanIdAndToken(String planId, String fcmToken) throws ExecutionException, InterruptedException {
        Map<String, Object> filters = Map.of(
                "plan_id", planId,
                "fcmToken", fcmToken
        );
        return findByFields(filters);
    }

    // Plan Id에 해당하는 모든 문서 반환
    public List<Alarm> getAlarmsByPlanId(String planId) throws ExecutionException, InterruptedException {
        return findAllByField("plan_id", planId);
    }
}
