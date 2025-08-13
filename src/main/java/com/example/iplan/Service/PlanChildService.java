package com.example.iplan.Service;

import com.example.iplan.DTO.PlanChildDTO;
import com.example.iplan.DTO.ScreenTimeDTO;
import com.example.iplan.Domain.PlanChild;
import com.example.iplan.Domain.ScreenTime;
import com.example.iplan.ExceptionHandler.CustomException;
import com.example.iplan.Repository.PlanChildRepository;
import com.example.iplan.Repository.SetScreenTimeRepository;
import com.example.iplan.auth.UserRepository;
import com.example.iplan.auth.Users;
import com.example.iplan.fcm.FcmToken;
import com.example.iplan.fcm.FcmTokenService;
import com.example.iplan.scheduler.PushSchedulerService;
import com.example.iplan.util.AES256Encryptor;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutionException;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlanChildService {

    private final PlanChildRepository planChildRepository;
    private final SetScreenTimeRepository setScreenTimeRepository;
    private final FcmTokenService fcmTokenService;
    private final PushSchedulerService pushSchedulerService;
    private final AlarmService alarmService;
    private final AES256Encryptor aes;
    private final UserRepository userRepository;

    /**
     * 새로운 계획을 추가하는 기능
     * repository에 값이 저장되면 add()를 통해 DocumentId가 자동생성된다.
     * @param planPostDto
     * @param user_id
     * @return
     * @throws ExecutionException
     * @throws InterruptedException
     */
    public ResponseEntity<Map<String, Object>> postChildNewPlan(PlanChildDTO planPostDto, String user_id) throws Exception {
        Map<String, Object> response = new HashMap<>();

        String encryptedTitle = aes.encrypt(planPostDto.getTitle());
        String encryptedMemo = aes.encrypt(planPostDto.getMemo());

        Users user = userRepository.findByEncryptedNickname(user_id).orElseThrow(() -> new IllegalArgumentException("User not found."));
        String hashedNickname = user.getNicknameHash();

        // 계획 포스트에 해시된 유저 아이디 필드 추가
        PlanChild planPost = PlanChild.builder()
                .user_id(user_id)
                .hashed_user_id(hashedNickname)
                .alarm(planPostDto.isAlarm())
                .memo(encryptedMemo)
                .category_id(planPostDto.getCategory_id())
                .title(encryptedTitle)
                .post_year(planPostDto.getPost_year())
                .post_month(planPostDto.getPost_month())
                .post_day(planPostDto.getPost_day())
                .plan_start_time(planPostDto.getPlan_start_time())
                .plan_end_time(planPostDto.getPlan_end_time())
                .is_completed(planPostDto.is_completed())
                .build();

        if (planPost.getUser_id() != null && !planPost.getUser_id().isEmpty()) {
            // 계획 저장
            planChildRepository.saveWithAutoIncrement(planPost);
            log.info("Saved successfully!! Plan ID: {}", planPost.getId()); // Auto Increment 된 문서 ID 바로 확인 가능

            // alarm == true 이고 시작 시간이 null 이 아닌 경우 푸시 예약
            if (planPost.isAlarm() && planPost.getPlan_start_time() != null && !planPost.getPlan_start_time().isBlank()) {

                // 사용자 FCM 토큰 모두 조회
                List<FcmToken> fcmTokens = fcmTokenService.getTokensByHashedUserId(hashedNickname);

                if (!fcmTokens.isEmpty()) {
                    for (FcmToken token : fcmTokens) {
                        // 1. 푸시 알림 예약 + Alarm 에 저장 (서버 재시작 시 다시 불러와야하므로)
                        pushSchedulerService.schedulePushNotification(planPost, token.getToken());
                        log.info("푸시 알림 예약 및 Alarm 저장 완료!!");
                    }
                } else {
                    log.warn("FCM 토큰이 존재하지 않아 푸시 예약 생략: user_id = {}", user_id);
                }
            }

            response.put("success", true);
            response.put("message", "계획이 정상적으로 추가되었습니다.");
            response.put("id", planPost.getId());

            //response = dayDataService.GenerateOrSaveDayPlanData(response, planPost, user_id);
            return new ResponseEntity<>(response, HttpStatus.OK);
        } else {
            throw new CustomException("유저 아이디가 올바르지 않습니다.", null, HttpStatus.INTERNAL_SERVER_ERROR, null);
        }
    }

    /**
     * 기존의 계획을 수정한다
     * @param planChildDTO 수정된 계획의 DTO
     * @param user_id 해당 계획 소유자 id
     */
    public ResponseEntity<Map<String, Object>> updateOriginalPlan(PlanChildDTO planChildDTO, String user_id) throws Exception {
        Map<String, Object> response = new HashMap<>();

        PlanChild existingPlan = planChildRepository.findEntityByDocumentId(planChildDTO.getId());

        if(existingPlan == null){
            log.info("계획이 존재하지 않아 수정 불가");
            throw new CustomException("계획 수정 실패", "해당 ID: "+ planChildDTO.getId() +"가 PlanChild 문서에 없습니다.", HttpStatus.NOT_FOUND, null);
        }
        if(!Objects.equals(existingPlan.getUser_id(), user_id)) {
            log.info("계획 수정 권한이 없음");
            throw new CustomException("계획 수정 실패", "해당 계획에 대한 수정 권한이 없습니다.", HttpStatus.UNAUTHORIZED, null);
        }

        // 1. 계획 업데이트
        PlanChild updatePlan = PlanChild.builder()
                .id(existingPlan.getId())
                .user_id(user_id)
                .hashed_user_id(existingPlan.getHashed_user_id())
                .alarm(planChildDTO.isAlarm())
                .memo(aes.encrypt(planChildDTO.getMemo()))
                .category_id(planChildDTO.getCategory_id())
                .title(aes.encrypt(planChildDTO.getTitle()))
                .post_year(planChildDTO.getPost_year())
                .post_month(planChildDTO.getPost_month())
                .post_day(planChildDTO.getPost_day())
                .plan_start_time(planChildDTO.getPlan_start_time())
                .plan_end_time(planChildDTO.getPlan_end_time())
                .is_completed(planChildDTO.is_completed())
                .build();
        try{
            planChildRepository.update(updatePlan);
            log.info("계획 업데이트 성공!!");
        }
        catch (Exception e){
            throw new CustomException("계획 업데이트에 실패했습니다", e.toString(), HttpStatus.INTERNAL_SERVER_ERROR,e );
        }

        // 2. 알림 설정에 따른 푸시 예약 처리
        boolean alarmWasOn = existingPlan.isAlarm();
        boolean alarmIsNowOn = planChildDTO.isAlarm();
        boolean hasStartTime = planChildDTO.getPlan_start_time() != null && !planChildDTO.getPlan_start_time().isBlank();

        if (alarmWasOn && !alarmIsNowOn) {
            // 알림이 꺼졌다면 예약 삭제 + Alarm 문서 삭제
            pushSchedulerService.cancelScheduledNotification(updatePlan.getId());
            alarmService.deleteAllByPlanId(updatePlan.getId());
            log.info("알림 설정 해제로 푸시 예약 및 Alarm 삭제 완료: planId = {}", updatePlan.getId());
        } else if (alarmIsNowOn && hasStartTime) {
            // 알림이 설정되었고 시작시간이 있다면 푸시 예약
            List<FcmToken> fcmTokens = fcmTokenService.getTokensByHashedUserId(existingPlan.getHashed_user_id());

            if (!fcmTokens.isEmpty()) {
                for (FcmToken token : fcmTokens) {
                    // 기존 예약이 있다면 내부적으로 덮어쓰기 처리됨
                    pushSchedulerService.schedulePushNotification(updatePlan, token.getToken());
//                    alarmService.saveAlarm(updatePlan.getId(), token.getToken());
                    log.info("푸시 알림 예약 및 Alarm 저장 완료: planId = {}, token = {}", updatePlan.getId(), token.getToken());
                }
            } else {
                log.warn("계획 수정 중 FCM 토큰이 존재하지 않아 푸시 예약 생략: user_id = {}", aes.decrypt(user_id));
            }
        }

        response.put("success", true);
        response.put("message", "계획이 정상적으로 업데이트 되었습니다");
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    /**
     * 날짜로 계획 반환
     */
    public List<PlanChildDTO> findAllPlanInTargetDate(String user_id, @JsonFormat(pattern = "yyy-MM-dd") String targetDate) throws ExecutionException, InterruptedException {
        List<PlanChild> planEntityList = planChildRepository.findPlanByDate(user_id, targetDate);

        List<PlanChildDTO> planDtoList = new ArrayList<>();

        try{
            if(!planEntityList.isEmpty()){
                for(PlanChild plan : planEntityList){
                    String start_time = plan.getPlan_start_time() != null && !plan.getPlan_start_time().isEmpty()
                            ? plan.getPlan_start_time() : "";
                    PlanChildDTO planDto = PlanChildDTO.builder()
                            .id(plan.getId())
                            .user_id(plan.getUser_id())
                            .title(plan.getTitle())
                            .post_day(targetDate)
                            .plan_start_time(start_time)
                            .is_completed(plan.is_completed())
                            .build();

                    planDtoList.add(planDto);
                }
            }
        }catch(Exception e){
            throw new CustomException("일시적 오류가 발생하였습니다.", e.toString(), HttpStatus.INTERNAL_SERVER_ERROR, e);
        }

        return planDtoList;
    }

    /**
     * 날짜 제한 없이 사용자의 모든 계획 목록을 반환
     */
    public Map<String, Object> getAllPlans(String childNickname) throws Exception {
        Map<String, Object> response = new HashMap<>();

        // childNickname 기준으로 Firestore에서 모든 계획 가져오기
        List<PlanChild> plans = planChildRepository.findEntityAll(childNickname);

        if (plans == null || plans.isEmpty()) {
            response.put("success", false);
            response.put("message", "등록된 계획이 없습니다.");
        } else {
            // Firestore의 문서 ID를 PlanChild 객체에 설정
            for (PlanChild plan : plans) {
                if (plan.getId() == null) {
                    plan.setId(UUID.randomUUID().toString()); // ID가 없으면 임시 ID 부여
                }
                plan.setUser_id(aes.decrypt(plan.getUser_id()));
                plan.setTitle(aes.decrypt(plan.getTitle()));
                plan.setMemo(aes.decrypt(plan.getMemo()));
            }
            response.put("success", true);
            response.put("plans", plans);
        }
        log.info("Get plan(child account) successfully!");
        return response;
    }

    /**
     * 단일 계획을 삭제한다
     * @param document_id
     * @return
     */
    public ResponseEntity<Map<String, Object>> DeletePlan(String document_id) {
        Map<String, Object> response = new HashMap<>();

        try{
            PlanChild plan = planChildRepository.findEntityByDocumentId(document_id);

            if (plan == null)
                throw new CustomException("계획 삭제 실패", "해당 ID: "+ document_id +"가 PlanChild 문서에 없습니다.", HttpStatus.NOT_FOUND, null);

            // 만약 알림 설정이 되어있는 계획이라면 예약된 푸시 알림 취소 + Alarm 컬렉션에서 삭제
            if (plan.isAlarm() && plan.getPlan_start_time() != null) {
                // 1. 푸시알림 예약 삭제 -> planId에 해당하는 fcmToken별 모든 예약
                pushSchedulerService.cancelScheduledNotification(plan.getId());

                // 2. Alarm 삭제
                alarmService.deleteAllByPlanId(plan.getId());
            }

            // 계획 삭제
            planChildRepository.delete(plan);
            log.info("계획 삭제 완료: {}", plan.getId());
        }
        catch (Exception e){
            throw new CustomException("계획 삭제 실패", e.toString(), HttpStatus.INTERNAL_SERVER_ERROR, e);
        }

        response.put("success", true);
        response.put("message", "계획이 정상적으로 삭제 되었습니다");
        return new ResponseEntity<>(response, HttpStatus.OK);

    }

    /**
     * 사용자가 목표 스크린타임을 설정한다
     * @param screenTimeDTO
     * @return
     */
    public ResponseEntity<Map<String, Object>> SetScreenTime(ScreenTimeDTO screenTimeDTO, String uid){
        Map<String, Object> response = new HashMap<>();

        ScreenTime newScreenTime = ScreenTime.builder()
                .user_id(uid)
                .date(screenTimeDTO.getDate())
                .deadLineTime(screenTimeDTO.getDeadLineTime())
                .goalTime(screenTimeDTO.getGoalTime())
                .build();

        try{
            setScreenTimeRepository.saveWithAutoIncrement(newScreenTime);
        }
        catch(Exception e){
            throw new CustomException("스크린 타임 설정에 실패했습니다", e.toString(), HttpStatus.INTERNAL_SERVER_ERROR, e);
        }

        response.put("success", true);
        response.put("message", "스크린 타임 정상적으로 설정 되었습니다");
        response.put("entity", newScreenTime);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    /**
     * 푸시알림을 보내야하는 미래의 알림 반환
     */
    public List<PlanChild> findFuturePlansWithAlarm(String userId) throws ExecutionException, InterruptedException {
        // 1. 유저 아이디와 알람 여부로 모든 계획 찾기
        List<PlanChild> plans = planChildRepository.findPlansWithAlarmByUser(userId);

        // 2. 필터링: 계획의 날짜 + 시작시간 > 현재시간
        List<PlanChild> futurePlans = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now(); // 현재 시간

        for (PlanChild plan : plans) {
            try {
                LocalDateTime planDateTime = LocalDateTime.of(
                        Integer.parseInt(plan.getPost_year()),
                        Integer.parseInt(plan.getPost_month()),
                        Integer.parseInt(plan.getPost_day()),
                        Integer.parseInt(plan.getPlan_start_time().split(":")[0]),
                        Integer.parseInt(plan.getPlan_start_time().split(":")[1])
                );

                long delay = Duration.between(now, planDateTime).toMillis();

                if (delay <= 0) {
                    log.info("이미 지난 계획 → 푸시 생략");
                } else {
                    futurePlans.add(plan);
                }
            } catch (Exception e) {
                log.info("푸시알림을 보낼 계획 필터링 도중 오류 발생");
            }
        }
        log.info("향후 푸시알림 보내야하는 계획 모두 가져오기 완료");
        return futurePlans;
    }

}