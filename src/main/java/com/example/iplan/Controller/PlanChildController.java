package com.example.iplan.Controller;

import com.example.iplan.DTO.PlanChildDTO;
import com.example.iplan.DTO.ScreenTimeDTO;
import com.example.iplan.Service.PlanChildService;
import com.google.firebase.database.annotations.NotNull;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * 특정 날짜의 단일 계획들에 대한 컨트롤러
 */
@Tag(name = "Plan CRUD", description = "아이 화면에서 계획을 추가하고, 확인하고, 수정하고, 삭제합니다.")
@RequiredArgsConstructor
@Controller
@RequestMapping("/plan")
public class PlanChildController {

    private final PlanChildService planChildService;

    /**
     * (목표 탭에서 계획 추가하기 버튼 클릭시) 해당 날짜에 단일 계획을 추가한다
     * @param request PlanChildDto
     * @param uid 유저 아이디
     * @return 성공 여부 및 오류 메세지
     * @throws ExecutionException
     * @throws InterruptedException
     */
    @Operation(summary = "계획 추가 POST", description = "당일의 계획을 추가한다.")
    @ApiResponses(value = {
            @ApiResponse(content = @Content(schema = @Schema(implementation = PlanChildDTO.class))),
    })
    @PostMapping("/addition")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> additionPlan(@RequestBody @NotNull PlanChildDTO request, @AuthenticationPrincipal String uid)
            throws ExecutionException, InterruptedException {

        return planChildService.postChildNewPlan(request, uid);
    }

    /**
     * (목표 탭 클릭시)추가된 계획 리스트를 전부 보여준다.(계획 제목만)
     * @return
     * @throws ExecutionException
     * @throws InterruptedException
     */
    @Operation(summary = "당일 계획 리스트 GET", description = "(목표 탭 클릭시)추가된 계획 리스트를 전부 보여준다.(계획 제목만)",
            parameters = {
                    @Parameter(name = "targetDate", description = "원하는 년/월/일", example = "2025-01-15", required = true)
            })
    @GetMapping("/dayPlanList/{targetDate}")
    public ResponseEntity<Map<String, Object>> showPlanList
    (@AuthenticationPrincipal String uid,
     @PathVariable @Parameter(description = "원하는 년/월/일", example = "2025-01-15") String targetDate) throws ExecutionException, InterruptedException {

        Map<String, Object> response = planChildService.findAllPlanList(uid, targetDate);
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
    (@PathVariable @Parameter(description = "해당 PlanChlid의 Id", example = "xicv3412zz") String documentID) throws ExecutionException, InterruptedException {
        return planChildService.findByPlanID(documentID);
    }

    /**
     * 특정 계획 수정
     * @param request
     * @param uid
     * @return
     * @throws ExecutionException
     * @throws InterruptedException
     */
    @Operation(summary = "단일 계획 업데이트 UPDATE", description = "특정 계획 데이터 값을 바꾼다.(계획 달성 체크의 경우도 해당), Id 필수")
    @PatchMapping("/update-plan")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updatePlan(@RequestBody @NotNull PlanChildDTO request, @AuthenticationPrincipal String uid) throws ExecutionException, InterruptedException {

        return planChildService.updateOriginalPlan(request, uid);
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
    @ResponseBody
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
    @ResponseBody
    public ResponseEntity<Map<String, Object>> setScreenTime(@RequestBody ScreenTimeDTO screenTime, @AuthenticationPrincipal String uid){
        return planChildService.SetScreenTime(screenTime, uid);
    }
}
