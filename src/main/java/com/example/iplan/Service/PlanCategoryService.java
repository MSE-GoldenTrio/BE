package com.example.iplan.Service;

import com.example.iplan.DTO.PlanCategoryDTO;
import com.example.iplan.Domain.PlanCategory;
import com.example.iplan.ExceptionHandler.CustomException;
import com.example.iplan.Repository.PlanCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ExecutionException;

@Service
@RequiredArgsConstructor
public class PlanCategoryService {

    private final PlanCategoryRepository planCategoryRepository;

    /**
     * 새로운 카테고리를 추가한다.
     * @param user_id
     * @param categoryName
     * @return
     */
    public ResponseEntity<Map<String, Object>> addCategory(String user_id, String categoryName){
        Map<String, Object> response = new HashMap<>();

        PlanCategory planCategory = PlanCategory.builder()
                .user_id(user_id)
                .name(categoryName)
                .build();

        try{
            planCategoryRepository.save(planCategory);
        }
        catch (Exception e){
            throw new CustomException("계획 카테고리 추가에 실패했습니다. Error: "+ e, HttpStatus.INTERNAL_SERVER_ERROR);
        }

        response.put("success", true);
        response.put("message", "계획 카테고리가 정상적으로 추가 되었습니다");
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    /**
     * 사용자가 설정한 카테고리 전부를 보여준다.
     * @param user_id
     * @return
     * @throws ExecutionException
     * @throws InterruptedException
     */
    public List<PlanCategoryDTO> findAllPlanCategory(String user_id) throws ExecutionException, InterruptedException {
        List<PlanCategory> planCategoryList = planCategoryRepository.findAllByFields(Map.of("user_id", user_id));

        ArrayList<PlanCategoryDTO> planCategoryDTOArrayList = new ArrayList<>();

        for(PlanCategory category : planCategoryList){
            PlanCategoryDTO planCategoryDTO = PlanCategoryDTO.builder()
                    .id(category.getId())
                    .name(category.getName())
                    .build();

            planCategoryDTOArrayList.add(planCategoryDTO);
        }

        return planCategoryDTOArrayList;
    }

    /**
     * 기존의 카테고리 이름을 변경한다.
     * @param user_id
     * @param planCategoryDTO
     * @return
     * @throws ExecutionException
     * @throws InterruptedException
     */
    public ResponseEntity<Map<String, Object>> updatePlanCategory(String user_id, PlanCategoryDTO planCategoryDTO) throws ExecutionException, InterruptedException {
        Map<String, Object> response = new HashMap<>();

        List<PlanCategory> planCategoryList = planCategoryRepository.findAllByFields(Map.of("user_id", user_id));

        Optional<PlanCategory> category = planCategoryList.stream()
                .filter(x -> Objects.equals(x.getId(), planCategoryDTO.getId()))
                .findFirst();

        if(category.isPresent()){
            PlanCategory planCategory = category.get();
            planCategory.setName(planCategoryDTO.getName());

            try{
                planCategoryRepository.update(planCategory);
            }
            catch (Exception e){
                throw new CustomException("계획 카테고리 수정에 실패했습니다. Error: "+ e, HttpStatus.INTERNAL_SERVER_ERROR);
            }

            response.put("success", true);
            response.put("message", "계획 카테고리가 정상적으로 수정 되었습니다");
            return new ResponseEntity<>(response, HttpStatus.OK);
        }else{
            throw new CustomException("해당 카테고리가 존재하지 않습니다.", HttpStatus.OK);
        }
    }

    /**
     * 카테고리를 삭제한다.
     * @param document_id
     * @return
     * @throws ExecutionException
     * @throws InterruptedException
     */
    public ResponseEntity<Map<String, Object>> deletePlanCategory(String document_id) throws ExecutionException, InterruptedException {
        Map<String, Object> response = new HashMap<>();

        try{
            PlanCategory planCategory = planCategoryRepository.findEntityByDocumentId(document_id);
            planCategoryRepository.delete(planCategory);
        }
        catch (Exception e){
            throw new CustomException("계획 카테고리 삭제에 실패했습니다. Error: "+ e, HttpStatus.INTERNAL_SERVER_ERROR);
        }

        response.put("success", true);
        response.put("message", "계획 카테고리가 정상적으로 삭제 되었습니다");
        return new ResponseEntity<>(response, HttpStatus.OK);

    }
}
