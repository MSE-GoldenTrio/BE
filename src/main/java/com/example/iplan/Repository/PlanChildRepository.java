package com.example.iplan.Repository;

import com.example.iplan.ExceptionHandler.CustomException;
import com.example.iplan.Repository.DefaultFirebaseRepository.DefaultFirebaseDBRepository;
import com.example.iplan.Domain.PlanChild;
import com.google.cloud.firestore.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Slf4j
@Repository
public class PlanChildRepository extends DefaultFirebaseDBRepository<PlanChild> {

    public PlanChildRepository(Firestore firestore) {
        super(firestore);
        setEntityClass(PlanChild.class);
        setCollectionName("PlanChild");
    }

    /**
     * 날짜를 통해 해당 날짜의 계획을 모두 가져 온다
     * @param user_id 유저 아이디
     * @param targetDate 어떤 계획이 있는지 알고 싶은 날짜
     * @return 해당 날짜 계획들(PlanChild List)
     */
    public List<PlanChild> findPlanByDate(String user_id, String targetDate) throws ExecutionException, InterruptedException {
        String[] dateArr = targetDate.split("-");

        Map<String, Object> filters = Map.of(
                "user_id", user_id,
                "post_year", dateArr[0],
                "post_month", dateArr[1],
                "post_date", dateArr[2]
        );
        return findAllByFields(filters);
    }

    /**
     * Plan ID로 해당하는 문서 반환
     */
    public PlanChild findPlanByID(String planId) throws ExecutionException, InterruptedException {
        PlanChild planChild = findEntityByDocumentId(planId);
        if (planChild == null) {
            throw new CustomException("계획을 찾지 못했습니다.", "해당 ID: "+ planId +"를 PlanChild 문서에서 찾을 수 없습니다.", HttpStatus.NOT_FOUND, null);
        }
        return planChild;
    }

    /**
     * 유저 닉네임과 알람 여부로 해당하는 모든 계획 반환
     */
    public List<PlanChild> findPlansWithAlarmByUser(String userId) throws ExecutionException, InterruptedException {
        Map<String, Object> filters = Map.of(
                "user_id", userId,
                "alarm", true
        );
        return findAllByFields(filters);
    }

}
