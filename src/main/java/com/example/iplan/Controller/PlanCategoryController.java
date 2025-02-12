package com.example.iplan.Controller;

import com.example.iplan.DTO.PlanCategoryDTO;
import com.example.iplan.Service.PlanCategoryService;
import com.google.firebase.database.annotations.NotNull;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Controller
@RequiredArgsConstructor
@RequestMapping("/plan-category")
@Tag(name = "계획 카테고리 관리 컨트롤러", description = "계획의 카테고리를 추가 등 관리한다.")
public class PlanCategoryController {

    private final PlanCategoryService planCategoryService;

    @Operation(summary = "카테고리 추가", parameters = {
            @Parameter(name = "categoryName", description = "추가하고싶은 카테고리 이름", example = "숙제", required = true)
    })
    @PostMapping("/addition/{categoryName}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> additionPlanCategory(@PathVariable @Parameter(description = "추가하고싶은 카테고리 이름", example = "숙제") String categoryName, @AuthenticationPrincipal String userId){
        return planCategoryService.addCategory(userId, categoryName);
    }

    @Operation(summary = "카테고리 GET", description = "사용자가 추가한 카테고리를 모두 보여준다.")
    @GetMapping("/categoryList/")
    @ResponseBody
    public List<PlanCategoryDTO> findAllUserCategory(@AuthenticationPrincipal String userId) throws ExecutionException, InterruptedException {
        return planCategoryService.findAllPlanCategory(userId);
    }

    @Operation(summary = "카테고리 삭제", parameters = {
            @Parameter(name = "documentID", description = "해당 카테고리 문서 ID", example = "sldifje1243", required = true)
    })
    @DeleteMapping("/delete/{documentID}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deletePlanCategory(@PathVariable @Parameter(example = "39827sdf") String documentID) throws ExecutionException, InterruptedException {
        return planCategoryService.deletePlanCategory(documentID);
    }
}
