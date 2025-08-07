package com.example.iplan.Service;

import com.example.iplan.Domain.PlanChild;
import com.example.iplan.ExceptionHandler.CustomException;
import com.example.iplan.Repository.PlanChildRepository;
import com.example.iplan.auth.UserRepository;
import com.example.iplan.auth.Users;
import com.example.iplan.util.AES256Encryptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ExecutionException;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlanParentService {

    private final PlanChildRepository planChildRepository;
    private final UserRepository userRepository;
    private final AES256Encryptor aes;

    /**
     * 여러 자녀 닉네임을 기반으로 모든 자녀의 계획 목록 반환
     */
    public Map<String, Object> getPlansForAllChildren(List<String> childNicknames) throws Exception {
        Map<String, Object> allPlans = new HashMap<>();

        for (String childNickname : childNicknames) {
            Users user = userRepository.findByHashValueNickName(DigestUtils.sha256Hex(childNickname)).orElseThrow(()-> new IllegalArgumentException("User not found"));
            String encryptedUserId = user.getNickname();
            Map<String, Object> childPlans = getAllPlans(encryptedUserId); // 재사용
            allPlans.put(childNickname, childPlans);
        }

        return allPlans;
    }

    /**
     * 날짜 제한 없이 사용자와 연동된 유저의 모든 계획 목록 반환
     */
    public Map<String, Object> getAllPlans(String encryptedChildNickname) throws Exception {
        Map<String, Object> response = new HashMap<>();

        // 암호화된 childNickname 기반으로 아이의 모든 계획 가져오기
        List<PlanChild> plans = planChildRepository.findEntityAll(encryptedChildNickname);

        if (plans == null || plans.isEmpty()) {
            response.put("success", false);
            response.put("message", "등록된 계획이 없습니다.");
            log.info("No plan for {}", aes.decrypt(encryptedChildNickname));
        } else {
            // Firestore의 문서 ID를 PlanChild 객체에 설정
            // 저장된 계획 중 ID가 없는 경우는 비정상 데이터이므로 오류 유도
            for (PlanChild plan : plans) {
                if (plan.getId() == null) {
                    throw new IllegalStateException("계획 ID가 없는 데이터가 존재합니다. DB 무결성을 확인해주세요.");
                }
                // 복호화해서 반환
                plan.setUser_id(aes.decrypt(plan.getUser_id()));
                plan.setTitle(aes.decrypt(plan.getTitle()));
                plan.setMemo(aes.decrypt(plan.getMemo()));
            }
            response.put("success", true);
            response.put("plans", plans);
            log.info("Get plan for {} successfully!", aes.decrypt(encryptedChildNickname));
        }
        return response;
    }

    /**
     * 아이의 단일 계획 반환 -> onSnapshot() 감지로 인한 해당 계획의 데이터 반환
     */
    public Map<String, Object> getPlanById(String childNickname, String planId) throws ExecutionException, InterruptedException {
        // planId에 해당하는 계획 가져오기
        PlanChild plan = planChildRepository.findPlanByID(planId);

        try {
            if (plan != null) {
                // 계획의 user_id와 일치한지 확인
                if (!Objects.equals(plan.getUser_id(), childNickname)) {
                    throw new CustomException("해당 계획에 대한 접근 권한이 없습니다.", null, HttpStatus.FORBIDDEN);  // 404 에러 반환
                }
                plan.setTitle(aes.decrypt(plan.getTitle()));
                plan.setMemo(aes.decrypt(plan.getMemo()));
                plan.setUser_id(aes.decrypt(plan.getUser_id()));
                return Map.of("success", true, "message", planId + " 계획 반환 성공", "plan", plan);
            } else {
                throw new CustomException("계획 불러오기 실패", planId + " ID의 계획이 존재하지 않음", HttpStatus.NOT_FOUND);
            }
        } catch (Exception e) {
            throw new CustomException("일시적 오류가 발생하였습니다.", e.toString(), HttpStatus.INTERNAL_SERVER_ERROR);
        }


    }

}
