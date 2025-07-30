package com.example.iplan.Service;

import com.example.iplan.DTO.ScreenTimeResultDTO;
import com.example.iplan.Domain.ScreenTime;
import com.example.iplan.Domain.ScreenTimeOCRResult;
import com.example.iplan.ExceptionHandler.CustomException;
import com.example.iplan.Repository.GetScreenTimeOCRRepository;
import com.example.iplan.Repository.InstalledAppsRepository;
import com.example.iplan.Repository.SetScreenTimeRepository;
import com.example.iplan.auth.UserRepository;
import com.example.iplan.auth.Users;
import com.example.iplan.util.AES256Encryptor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ExecutionException;

@Service
@RequiredArgsConstructor
public class ParentScreenTimeService {
    private final SetScreenTimeRepository setScreenTimeRepository;
    private final GetScreenTimeOCRRepository getScreenTimeOCRRepository;
    private final UserRepository userRepository;
    private final AES256Encryptor aes;

    public ResponseEntity<Map<String, Object>> getScreenTimeGraph(String user_id, String targetDate) throws Exception {
        List<String> child_id = getTargetChildID(user_id);

        if(!isCollectLinkedID(user_id, child_id))
            throw new CustomException("child_id 중 올치 않은 계정이 있습니다.", HttpStatus.BAD_REQUEST);

        Map<String, Object> response = new HashMap<>();

        List<ScreenTimeResultDTO> child_OCRresult_list = new ArrayList<>();

        for(String child : child_id){
            ScreenTimeOCRResult result = getScreenTimeOCRRepository.findByDate(child, targetDate);

            if(result != null) {
                ScreenTimeResultDTO resultDTO = ScreenTimeResultDTO.fromEntity(result, aes);
                child_OCRresult_list.add(resultDTO);
            }
        }

        if(child_OCRresult_list.isEmpty()){
            response.put("success", false);
            response.put("message", "연동된 아이들 중 스크린타임 분석 그래프 대한 데이터가 존재하지 않습니다.");

            return new ResponseEntity<>(response, HttpStatus.OK);
        }

        response.put("entity", child_OCRresult_list);
        response.put("success", true);

        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    public ResponseEntity<Map<String, Object>> getScreenTime(String user_id, String targetDate) throws Exception {
        List<String> child_id = getTargetChildID(user_id);

        if(!isCollectLinkedID(user_id, child_id))
            throw new CustomException("child_id: "+ child_id + "는 연동된 계정이 아닙니다.", HttpStatus.BAD_REQUEST);

        Map<String, Object> response = new HashMap<>();
        List<ScreenTime> child_screenTime_list = new ArrayList<>();

        for(String child : child_id){
            ScreenTime result = setScreenTimeRepository.findByDate(child, targetDate);
            if(result != null){
                result.setUser_id(aes.decrypt(result.getUser_id()));
                child_screenTime_list.add(result);
            }
        }

        if(child_screenTime_list.isEmpty()){
            response.put("success", false);
            response.put("message", "연동된 아이들 중 스크린타임 타임셋에 대한 데이터가 존재하지 않습니다.");

            return new ResponseEntity<>(response, HttpStatus.OK);
        }

        response.put("entity", child_screenTime_list);
        response.put("success", true);

        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    public Map<String, Object> getOneGraphData(String parentEncryptedNickname, String graphId, String childId) throws ExecutionException, InterruptedException {
        Map<String, Object> response = new HashMap<>();

        ScreenTimeOCRResult graphData = getScreenTimeOCRRepository.findEntityByDocumentId(graphId);

        try{
            if(graphData != null){
                if(!Objects.equals(graphData.getUser_id(), childId)){
                    throw new CustomException("해당 그래프 데이터에 대한 접근 권한이 없습니다.", HttpStatus.NOT_ACCEPTABLE);
                }
                ScreenTimeResultDTO resultDTO = ScreenTimeResultDTO.fromEntity(graphData, aes);
                response.put("success", true);
                response.put("entity", resultDTO);
            }
        }catch (Exception e){
            throw new CustomException("서버 오류 발생: " + e, HttpStatus.INTERNAL_SERVER_ERROR);
        }

        return response;
    }

    public Map<String, Object> getOneTimeData(String parentEncryptedNickname, String docId, String childId) throws ExecutionException, InterruptedException {
        Map<String, Object> response = new HashMap<>();

        ScreenTime screenTime = setScreenTimeRepository.findEntityByDocumentId(docId);

        try{
            if(screenTime != null){
                if(!Objects.equals(screenTime.getUser_id(), childId)){
                    throw new CustomException("해당 스크린타임 시간 데이터에 대한 접근 권한이 없습니다.", HttpStatus.NOT_ACCEPTABLE);
                }
                screenTime.setUser_id(aes.decrypt(screenTime.getUser_id()));
                response.put("success", true);
                response.put("entity", screenTime);
            }
        }catch (Exception e){
            throw new CustomException("서버 오류 발생: " + e, HttpStatus.INTERNAL_SERVER_ERROR);
        }

        return response;
    }

    private boolean isCollectLinkedID(String user_id, List<String> child_id){
        for(String child : child_id){
            if(!userRepository.isLinkedToChild(user_id, child)){
                return false;
            }
        }

        return true;
    }

    private List<String> getTargetChildID(String parent_id){;
        Users parent = userRepository.findByEncryptedNickname(parent_id).orElseThrow(() -> new IllegalArgumentException("User not found."));
        List<String> child_id_list = parent != null ? parent.getLinked_id() : null;

        if(child_id_list == null){
            throw new CustomException("해당 부모와 연동된 아이가 존재하지않습니다.", HttpStatus.NOT_FOUND);
        }

        System.out.println("Target Child ID List: " + child_id_list.stream().toString());

        return child_id_list;
    }
}
