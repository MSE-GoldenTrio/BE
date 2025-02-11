package com.example.iplan.Service;

import com.example.iplan.DTO.DayDataDTO;
import com.example.iplan.DTO.PlanChildDTO;
import com.example.iplan.Domain.DayData;
import com.example.iplan.Domain.RewardChild;
import com.example.iplan.Domain.ScreenTimeOCRResult;
import com.example.iplan.ExceptionHandler.CustomException;
import com.example.iplan.Repository.DayDataRepository;
import com.example.iplan.Repository.GetScreenTimeOCRRepository;
import com.example.iplan.Repository.RewardChildRepository;
import io.netty.util.internal.StringUtil;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Service
@RequiredArgsConstructor
public class CalendarService {

    private final DayDataRepository dayDataRepository;
    private final RewardChildRepository rewardChildRepository;
    private final PlanChildService planChildService;
    private final GetScreenTimeOCRRepository getScreenTimeOCRRepository;

    public ResponseEntity<Map<String, Object>> getAllCalendarData(String yearMonth, String user_id){
        Map<String, Object> response = new HashMap<>();
        List<DayDataDTO> dayDataDTOList;
        try{
            dayDataDTOList = dayDataRepository.findTargetMonthData(user_id, yearMonth);
        }catch(Exception e){
            throw new CustomException(e.getMessage(), HttpStatus.BAD_REQUEST);
        }

        response.put("success", true);
        response.put("entity", dayDataDTOList);
        response.put("message", "해당 달의 데이터 가져오기에 성공하였습니다");

        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    public ResponseEntity<Map<String, Object>> getTargetDateData(String yearMonthDate, String user_id) throws ExecutionException, InterruptedException {
        Map<String, Object> response = new HashMap<>();
        DayData dayData;

        try{
            dayData = dayDataRepository.findTargetDayData(user_id, yearMonthDate);

            if(dayData == null){
                response.put("success", true);
                response.put("message", "해당 날짜는 아직 아무 데이터가 없습니다");
                return new ResponseEntity<>(response, HttpStatus.OK);
            }
        }catch (Exception e){
            throw new CustomException(e.getMessage(), HttpStatus.BAD_REQUEST);
        }

        ScreenTimeOCRResult screenTimeOCRResult= getScreenTimeOCRRepository.findByDate(user_id, yearMonthDate);
        Map<String, Object> PlanListResponse = planChildService.findAllPlanList(user_id, yearMonthDate);
        List<PlanChildDTO> planChildDTOList = new ArrayList<>();
        if (PlanListResponse.get("entity") instanceof List<?> list) {
            if (!list.isEmpty() && list.get(0) instanceof PlanChildDTO) {
                planChildDTOList = (List<PlanChildDTO>) list;
            }
        }

        boolean screenTimeSuccess = screenTimeOCRResult != null && screenTimeOCRResult.isSuccess();

        RewardChild rewardChild = rewardChildRepository.findRewardChildByDay(user_id, yearMonthDate);
        boolean rewardedSuccess = rewardChild != null && rewardChild.isSuccess();
        String rewardContent = rewardChild != null ? rewardChild.getContent() : StringUtils.EMPTY;

        DayDataDTO dayDataDTO = DayDataDTO.builder()
                .id(dayData.getId())
                .user_id(user_id)
                .date(yearMonthDate)
                .day(yearMonthDate.split("-")[2])
                .planChildDTOList(planChildDTOList)
                .screenTime_goal(screenTimeSuccess)
                .reward_content(rewardContent)
                .is_reward(rewardedSuccess)
                .build();

        response.put("success", true);
        response.put("entity", dayDataDTO);
        response.put("message", "특정 날짜 데이터 가져오기 성공");

        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}
