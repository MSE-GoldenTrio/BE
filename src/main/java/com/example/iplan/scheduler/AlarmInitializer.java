package com.example.iplan.scheduler;

import com.example.iplan.Domain.Alarm;
import com.example.iplan.Domain.PlanChild;
import com.example.iplan.Repository.AlarmRepository;
import com.example.iplan.Repository.PlanChildRepository;
import com.example.iplan.Service.PlanChildService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AlarmInitializer implements ApplicationListener<ApplicationReadyEvent> {

    private final AlarmRepository alarmRepository;
    private final PlanChildRepository planChildRepository;
    private final PushSchedulerService pushSchedulerService;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        log.info("AlarmInitializer 실행 시작됨");
        try {
            List<Alarm> alarms = alarmRepository.getAllAlarms(); // alarm 컬렉션 전부 가져오기
            if (alarms.isEmpty()) {
                log.info("복구할 알람 없음");
                return;
            }

            for (Alarm alarm : alarms) {
                PlanChild plan = planChildRepository.findPlanByID(alarm.getPlan_id());

                if (plan != null) {
                    pushSchedulerService.schedulePushNotification(plan, alarm.getFcmToken());
                    log.info("푸시 복구 완료: {}", plan.getTitle());
                }
            }

        } catch (Exception e) {
            log.error("알람 복구 실패: {}", e.getMessage());
        }
    }
}

