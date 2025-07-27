package com.example.iplan.Controller;

import com.example.iplan.DTO.PlanChildDTO;
import com.example.iplan.DTO.ScreenTimeDTO;
import com.example.iplan.Domain.PlanChild;
import com.example.iplan.Repository.PlanChildRepository;
import com.example.iplan.Service.PlanChildService;
import com.example.iplan.auth.oauth2.CustomOAuth2UserDetails;
import com.google.firebase.database.annotations.NotNull;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Tag(name = "Plan CRUD", description = "아이 화면에서 계획을 추가하고, 확인하고, 수정하고, 삭제합니다.")
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("child/plan")
public class PlanChildController {

    private final PlanChildService planChildService;
    private final PlanChildRepository planChildRepository;

    /**
     * (목표 탭에서 계획 추가하기 버튼 클릭시) 해당 날짜에 단일 계획을 추가한다
     * @param request PlanChildDto
     * @param user 인증객체
     * @return 성공 여부 및 오류 메세지
     * @throws ExecutionException
     * @throws InterruptedException
     */
    @Operation(summary = "계획 추가 POST", description = "당일의 계획을 추가한다.")
    @ApiResponses(value = {
            @ApiResponse(content = @Content(schema = @Schema(implementation = PlanChildDTO.class))),
    })
    @PostMapping("/addition")
    public ResponseEntity<Map<String, Object>> additionPlan(@RequestBody @NotNull PlanChildDTO request, @AuthenticationPrincipal CustomOAuth2UserDetails user)
            throws Exception {
        System.out.println("Child plan addition API received!");

        String childNickname = user.getUsername();
        return planChildService.postChildNewPlan(request, childNickname);
    }

    /**
     * 사용자의 전체 계획 목록 반환(날짜 상관 없음)
     * @param user
     * @return
     * @throws ExecutionException
     * @throws InterruptedException
     */
    @Operation(summary = "계획 목록 조회 GET", description = "해당 사용자의 전체 계획 목록을 가져온다.")
    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> getPlanList(@AuthenticationPrincipal CustomOAuth2UserDetails user) throws Exception {
        String nickname = user.getUsername();
        Map<String, Object> response = planChildService.getAllPlans(nickname);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    /**
     * (목표 탭에서 해당 날짜에서 특정 계획 클릭시)특정 계획의 세부사항을 확인한다.
     * @param documentID
     * @return
     * @throws ExecutionException
     * @throws InterruptedException
     */
    @Operation(summary = "단일 계획 GET", description = "(목표 탭에서 해당 날짜에서 특정 계획 클릭시)특정 계획의 세부사항을 확인한다.",
            parameters = {
                    @Parameter(name = "documentID", description = "해당 PlanChlid의 Id", example = "xicv3412zz", required = true)
            })
    @GetMapping("/detail/{documentID}")
    public ResponseEntity<Map<String, Object>> showPlanDetail
    (@PathVariable @Parameter(description = "해당 PlanChlid의 Id", example = "xicv3412zz") String documentID) throws Exception {
        Map<String, Object> response = new HashMap<>();
        PlanChild planChild = planChildRepository.findPlanByID(documentID);
        response.put("success", true);
        response.put("message", "해당 ID PlanChild 문서 찾는데 성공했습니다.");
        response.put("entity", planChild);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    /**
     * 특정 계획 수정
     * @param request
     * @param user
     * @return
     * @throws ExecutionException
     * @throws InterruptedException
     */
    @Operation(summary = "단일 계획 업데이트 UPDATE", description = "특정 계획 데이터 값을 바꾼다.(계획 달성 체크의 경우도 해당), Id 필수")
    @PatchMapping("/update-plan")
    public ResponseEntity<Map<String, Object>> updatePlan(@RequestBody @NotNull PlanChildDTO request, @AuthenticationPrincipal CustomOAuth2UserDetails user) throws Exception {
        String childNickname = user.getUsername();
        return planChildService.updateOriginalPlan(request, childNickname);
    }

    /**
     * 특정 계획 삭제 버튼 클릭시
     * @param documentID
     * @return
     * @throws ExecutionException
     * @throws InterruptedException
     */
    @Operation(summary = "단일 계획 삭제 DELETE", description = "특정 계획 삭제 버튼 클릭시",
            parameters = {
                    @Parameter(name = "documentID", description = "해당 PlanChlid의 Id", example = "xicv3412zz", required = true)
            })
    @DeleteMapping("/delete-plan/{documentID}")
    public ResponseEntity<Map<String, Object>> deletePlan(@PathVariable @Parameter(description = "해당 PlanChlid의 Id", example = "xicv3412zz")String documentID) throws ExecutionException, InterruptedException {
        return planChildService.DeletePlan(documentID);
    }

    /**
     * 목표 탭에서 스크린 타임 측정 클릭시 목표 시간 설정
     * @param screenTime
     * @return
     */
    @Operation(summary = "스크린 타임 목표 설정", description = "목표 탭에서 스크린 타임 측정 클릭시 목표 시간 설정")
    @PostMapping("/screen-time-set")
    public ResponseEntity<Map<String, Object>> setScreenTime(@RequestBody ScreenTimeDTO screenTime, @AuthenticationPrincipal CustomOAuth2UserDetails user){
        String childNickname = user.getUsername();
        return planChildService.SetScreenTime(screenTime, childNickname);
    }
}
