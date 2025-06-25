package com.example.iplan.Controller;

import com.example.iplan.Service.PlanParentService;
import com.example.iplan.auth.oauth2.CustomOAuth2UserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Tag(name = "Plan CRUD", description = "부모 화면에서 아이 계획 가져옴")
@Slf4j
@RequiredArgsConstructor
@Controller
@RequestMapping("parent/plan")
public class PlanParentController {

    private final PlanParentService planParentService;

    /**
     * 부모의 linked_id를 기반으로 모든 자녀의 전체 계획 목록 반환
     */
    @Operation(summary = "부모의 자녀 전체 계획 목록 조회 GET", description = "해당 부모와 연동된 모든 자녀의 계획 목록을 가져온다.")
    @GetMapping("/list")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getAllChildPlanList(
            @AuthenticationPrincipal CustomOAuth2UserDetails user) throws ExecutionException, InterruptedException {

        List<String> linkedIds = user.getUser().getLinked_id();

        // 예외 처리: 연동된 자녀가 없는 경우
        if (linkedIds == null || linkedIds.isEmpty()) {
            return new ResponseEntity<>(Map.of("success", false, "message", "연동된 자녀가 없습니다."), HttpStatus.BAD_REQUEST);
        }

        Map<String, Object> response = planParentService.getPlansForAllChildren(linkedIds);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }


    /**
     * 부모의 linked_id를 기반으로 아이의 전체 계획 목록 반환
     */
    @Operation(summary = "부모의 아이 계획 목록 조회 GET", description = "해당 부모와 연동된 아이의 전체 계획 목록을 가져온다.")
    @Parameter(description = "조회할 자녀 인덱스")
    @GetMapping("/list/{index}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getPlanListByChildIndex(
            @PathVariable int index,
            @AuthenticationPrincipal CustomOAuth2UserDetails user) throws ExecutionException, InterruptedException {

        String parentNickname = user.getUsername();
        List<String> linkedIds = user.getUser().getLinked_id();

        // 예외 처리: 연동된 자녀가 없는 경우
        if (linkedIds == null || linkedIds.isEmpty()) {
            return new ResponseEntity<>(Map.of("message", "연동된 자녀가 없습니다."), HttpStatus.BAD_REQUEST);
        }

        // 예외 처리: 인덱스 범위 초과
        if (index < 0 || index >= linkedIds.size()) {
            return new ResponseEntity<>(Map.of("message", "잘못된 자녀 인덱스입니다."), HttpStatus.BAD_REQUEST);
        }

        String childNickname = linkedIds.get(index);

        Map<String, Object> response = planParentService.getAllPlans(childNickname);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

}
