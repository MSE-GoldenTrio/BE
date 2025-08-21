package com.example.iplan.scheduler;

import com.example.iplan.Domain.PlanChild;
import com.example.iplan.Service.AlarmService;
import com.example.iplan.fcm.FcmRequestService;
import com.example.iplan.util.AES256Encryptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class PushSchedulerService {

    private final TaskScheduler taskScheduler;
    private final FcmRequestService fcmRequestService;
    private final AlarmService alarmService;
    private final AES256Encryptor aes;

    // planId -> (fcmToken -> future)
    private Map<String, Map<String, ScheduledFuture<?>>> scheduledTasks = new ConcurrentHashMap<>();


    /**
     * 푸시알림 예약
     * @param plan
     * @param fcmToken
     */
    public void schedulePushNotification(PlanChild plan, String fcmToken) {
        if (!plan.isAlarm()) return;

        LocalDateTime targetTime = LocalDateTime.of(
                Integer.parseInt(plan.getPost_year()),
                Integer.parseInt(plan.getPost_month()),
                Integer.parseInt(plan.getPost_day()),
                Integer.parseInt(plan.getPlan_start_time().split(":")[0]),
                Integer.parseInt(plan.getPlan_start_time().split(":")[1])
        );

        long delay = Duration.between(LocalDateTime.now(), targetTime).toMillis();

        if (delay <= 0) {
            log.info("이미 지난 계획 → 푸시 생략");
            return;
        }

        // 기존 예약이 있으면 먼저 취소
        Map<String, ScheduledFuture<?>> tokenMap = scheduledTasks.get(plan.getId());
        if (tokenMap != null) {
            ScheduledFuture<?> existingFuture = tokenMap.get(fcmToken);
            if (existingFuture != null && !existingFuture.isCancelled()) {
                existingFuture.cancel(false);
                log.info("기존 푸시 예약 취소: planId = {}, fcmToken = {}", plan.getId(), fcmToken);
            }
        }

        PushTask task = new PushTask(plan, fcmToken, fcmRequestService, alarmService, aes);
        Date executeAt = new Date(System.currentTimeMillis() + delay);
        // 예약된 작업 저장
        ScheduledFuture<?> future = taskScheduler.schedule(task, executeAt);


        // planId 기준으로 하위에 fcmToken별 future 저장
        scheduledTasks
                .computeIfAbsent(plan.getId(), k -> new ConcurrentHashMap<>())
                .put(fcmToken, future);

        log.info("푸시 예약 완료: {} / 시간: {} / 토큰: {}", plan.getTitle(), targetTime, fcmToken);

        // 푸시 예약 성공 후에만 알람 저장
        try {
            alarmService.saveAlarm(plan.getId(), fcmToken);
            log.info("알람 저장 성공!");
        } catch (Exception e) {
            log.error("알람 저장 실패: planId = {}, token = {}, error = {}", plan.getId(), fcmToken, e.getMessage());
        }
    }

    /**
     * 푸시알림 예약 취소(삭제)
     * @param planId
     */
    public void cancelScheduledNotification(String planId) {
        // planId에 해당하는 모든 fcmToken별 예약 작업 가져오기
        Map<String, ScheduledFuture<?>> futures = scheduledTasks.remove(planId);

        if (futures == null || futures.isEmpty()) {
            log.info("{} 계획에 대한 푸시 예약이 존재하지 않습니다.", planId);
            return;
        }

        // 각 FCM 토큰에 대한 예약 작업 취소
        for (Map.Entry<String, ScheduledFuture<?>> entry : futures.entrySet()) {
            ScheduledFuture<?> future = entry.getValue();
            if (future != null && !future.isCancelled()) {
                future.cancel(false);
                log.info("푸시 예약 취소 완료: planId = {}, fcmToken = {}", planId, entry.getKey());
            }
        }
    }
}


