package com.example.iplan.Repository;

import com.example.iplan.Repository.DefaultFirebaseRepository.DefaultFirebaseDBRepository;
import com.example.iplan.Domain.PlanChild;
import com.google.cloud.firestore.*;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

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
     * @throws ExecutionException
     * @throws InterruptedException
     */
    public List<PlanChild> findByDate(String user_id, String targetDate) throws ExecutionException, InterruptedException {
        String[] dateArr = targetDate.split("-");

        Map<String, Object> filters = Map.of(
                "user_id", user_id,
                "post_year", dateArr[0],
                "post_month", dateArr[1],
                "post_date", dateArr[2]
        );
        return findAllByFields(filters);
    }
}
