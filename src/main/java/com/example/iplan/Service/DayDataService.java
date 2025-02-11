package com.example.iplan.Service;

import com.example.iplan.Domain.DayData;
import com.example.iplan.Domain.PlanChild;
import com.example.iplan.Repository.DayDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Service
@RequiredArgsConstructor
public class DayDataService {

    private final DayDataRepository dayDataRepository;

    public Map<String, Object> GenerateOrSaveDayPlanData(Map<String, Object> response, PlanChild planChild, String user_id) throws ExecutionException, InterruptedException {
        String yearMonthDate = planChild.getPost_year() + "-" + planChild.getPost_month() + "-" + planChild.getPost_date();
        DayData targetDayData = dayDataRepository.findTargetDayData(user_id, yearMonthDate);

        if(targetDayData == null){
            List<String> newPlanList = new ArrayList<>();
            newPlanList.add(planChild.getId());

            DayData dayData = DayData.builder()
                    .user_id(user_id)
                    .year(planChild.getPost_year())
                    .month(planChild.getPost_month())
                    .date(planChild.getPost_date())
                    .plan_idList(newPlanList)
                    .is_rewarded(false)
                    .build();

            dayDataRepository.save(dayData);
            response.put("message", "새로운 하루 데이터를 생성 후 계획을 추가합니다.");
        }else{
            targetDayData.getPlan_idList().add(planChild.getId());
            dayDataRepository.update(targetDayData);
            response.put("message", "기존 하루 데이터에 새로운 계획을 추가합니다.");
        }

        return response;
    }
}
