package com.example.iplan.Service;

import com.example.iplan.Domain.Alarm;
import com.example.iplan.Domain.PlanChild;
import com.example.iplan.ExceptionHandler.CustomException;
import com.example.iplan.Repository.AlarmRepository;
import com.example.iplan.Repository.PlanChildRepository;
import com.example.iplan.scheduler.PushSchedulerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ExecutionException;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlarmService {
    private final AlarmRepository alarmRepository;

    /**
     * 푸시알람 저장
     */
    public void saveAlarm(String planId, String fcmToken) throws ExecutionException, InterruptedException {
        try {
            if(alarmRepository.getAlarmByPlanIdAndToken(planId, fcmToken) != null) {
                log.info("해당 계획에 대한 알람이 이미 존재합니다.");
                return;
            }
            Alarm alarm = Alarm.builder()
                    .plan_id(planId)
                    .fcmToken(fcmToken)
                    .build();
            alarmRepository.saveWithAutoIncrement(alarm);
            log.info("알람 저장 성공: {}", alarm.getId());
        } catch (Exception e) {
            throw new CustomException("알림 저장 성공에 실패했습니다. Error: " + e, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /** PlanId와 FcmToken 으로 푸시알림 삭제
     * -> 푸시알림을 성공적으로 보낸 이후 더 이상 필요없는 경우
     */
    public void deleteOneAlarm(String planId, String fcmToken) throws ExecutionException, InterruptedException {
        try {
            Alarm alarm = alarmRepository.getAlarmByPlanIdAndToken(planId, fcmToken);
            if (alarm != null) {
                alarmRepository.delete(alarm);
                log.info("푸시알림 전송으로 인한 알림 삭제 완료: alarmId = {}", alarm.getId());
            } else {
                log.info("삭제할 알림이 존재하지 않음: planId = {} / fcmToken = {}", planId, fcmToken);
            }
        } catch (Exception e) {
            throw new CustomException("알림을 컬렉션에서 삭제에 실패했습니다. Error: " + e, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * PlanId로 푸시알림 삭제
     * -> 계획을 삭제하여 알림을 보낼 필요가 없는 경우
     * -> 이 경우에는 해당 계획에 대한 모든 알림 문서를 삭제해야함 (FcmToken 이 여러개인 경우)
     */
    public void deleteAllByPlanId(String planId) throws ExecutionException, InterruptedException {
        try {
            List<Alarm> alarms = alarmRepository.getAlarmsByPlanId(planId);
            if (!alarms.isEmpty()) {
                for (Alarm alarm : alarms) {
                    alarmRepository.delete(alarm);
                    log.info("계획 삭제로 인한 알림 삭제 완료: alarmId = {}", alarm.getId());
                }
            } else {
                log.info("삭제할 알림이 존재하지 않음: planId = {}", planId);
            }
        } catch (Exception e) {
            throw new CustomException("알림을 컬렉션에서 삭제에 실패했습니다. Error: " + e, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

}
