package com.example.iplan.Controller;

import com.example.iplan.DTO.PlanCategoryDTO;
import com.example.iplan.Service.PlanCategoryService;
import com.google.firebase.database.annotations.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Controller
@RequiredArgsConstructor
@RequestMapping("/plan-category")
public class PlanCategoryController {

    private final PlanCategoryService planCategoryService;

    @PostMapping("/addition/{documentID}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> additionPlanCategory(@RequestBody @NotNull PlanCategoryDTO request, @PathVariable String documentID){
        return planCategoryService.addCategory(documentID, request);
    }

    @GetMapping("/categoryList/{documentID}")
    @ResponseBody
    public List<PlanCategoryDTO> findAllUserCategory(@PathVariable String documentID) throws ExecutionException, InterruptedException {
        return planCategoryService.findAllPlanCategory(documentID);
    }

    @PatchMapping("/update/{documentID}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updatePlanCategory(@PathVariable String documentID, PlanCategoryDTO planCategoryDTO) throws ExecutionException, InterruptedException {
        return planCategoryService.updatePlanCategory(documentID, planCategoryDTO);
    }

    @DeleteMapping("/delete/{documentID}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deletePlanCategory(@PathVariable String documentID) throws ExecutionException, InterruptedException {
        return planCategoryService.deletePlanCategory(documentID);
    }
}
