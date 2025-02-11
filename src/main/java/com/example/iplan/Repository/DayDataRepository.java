package com.example.iplan.Repository;

import com.example.iplan.DTO.DayDataDTO;
import com.example.iplan.Domain.DayData;
import com.example.iplan.Domain.PlanChild;
import com.example.iplan.Repository.DefaultFirebaseRepository.DefaultFirebaseDBRepository;
import com.google.cloud.firestore.Firestore;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Repository
public class DayDataRepository extends DefaultFirebaseDBRepository<DayData> {
    public DayDataRepository(Firestore firestore) {
        super(firestore);
        setEntityClass(DayData.class);
        setCollectionName("DayData");
    }

    /**
     * 달력 탭 눌렀을 때 해당 달의 모든 날의 목표 달성 여부를 찾아 반환한다.
     * @param user_id
     * @param yearMonth "yyyy-MM"형식
     * @return
     */
    public List<DayDataDTO> findTargetMonthData(String user_id, String yearMonth) throws ExecutionException, InterruptedException {
        String[] yearMonthArr = yearMonth.split("-");
        List<DayDataDTO> dayDataDTOS = new ArrayList<>();

        Map<String, Object> filters = Map.of(
                "user_id", user_id,
                "year", yearMonthArr[0],
                "month", yearMonthArr[1]
        );

        List<DayData> dayData = findAllByFields(filters);
        for (DayData dayDatum : dayData) {
            DayDataDTO dataDTO = DayDataDTO.builder()
                    .id(dayDatum.getId())
                    .user_id(dayDatum.getUser_id())
                    .date(yearMonth + "-" + dayDatum.getDate())
                    .day(dayDatum.getDate())
                    .is_reward(dayDatum.getIs_rewarded())
                    .build();

            dayDataDTOS.add(dataDTO);
        }

        return dayDataDTOS;
    }

    /**
     * 달력에서 해당 날짜 클릭시 해당 날짜의 데이터를 모두 가져온다
     * @param user_id
     * @param yearMonthDate "yyyy-MM-dd" 형식
     * @return
     */
    public DayData findTargetDayData(String user_id, String yearMonthDate) throws ExecutionException, InterruptedException {
        String[] dateArr = yearMonthDate.split("-");

        Map<String, Object> filters = Map.of(
                "user_id", user_id,
                "year", dateArr[0],
                "month", dateArr[1],
                "date", dateArr[2]
        );

        return findByFields(filters);
    }
}
