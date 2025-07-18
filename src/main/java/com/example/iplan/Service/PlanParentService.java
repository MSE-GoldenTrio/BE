package com.example.iplan.Service;

import com.example.iplan.Domain.PlanChild;
import com.example.iplan.Repository.PlanChildRepository;
import com.example.iplan.util.AES256Encryptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlanParentService {

    private final PlanChildRepository planChildRepository;
    private final AES256Encryptor aes;

    /**
     * 여러 자녀 닉네임을 기반으로 모든 자녀의 계획 목록 반환
     */
    public Map<String, Object> getPlansForAllChildren(List<String> childNicknames) throws Exception {
        Map<String, Object> allPlans = new HashMap<>();

        for (String childNickname : childNicknames) {
            Map<String, Object> childPlans = getAllPlans(childNickname); // 재사용
            allPlans.put(childNickname, childPlans);
        }

        return allPlans;
    }

    /**
     * 날짜 제한 없이 사용자와 연동된 유저의 모든 계획 목록 반환
     */
    public Map<String, Object> getAllPlans(String childNickname) throws Exception {
        Map<String, Object> response = new HashMap<>();

        // childNickname 기반으로 아이의 모든 계획 가져오기
        List<PlanChild> plans = planChildRepository.findEntityAll(childNickname);

        if (plans == null || plans.isEmpty()) {
            response.put("success", false);
            response.put("message", "등록된 계획이 없습니다.");
        } else {
            // Firestore의 문서 ID를 PlanChild 객체에 설정
            // 저장된 계획 중 ID가 없는 경우는 비정상 데이터이므로 오류 유도
            for (PlanChild plan : plans) {
                if (plan.getId() == null) {
                    throw new IllegalStateException("계획 ID가 없는 데이터가 존재합니다. DB 무결성을 확인해주세요.");
                }

                plan.setTitle(aes.decrypt(plan.getTitle()));
                plan.setMemo(aes.decrypt(plan.getMemo()));
            }
            response.put("success", true);
            response.put("plans", plans);
        }
        log.info("Get plan for {} successfully!", childNickname);
        return response;
    }

}
