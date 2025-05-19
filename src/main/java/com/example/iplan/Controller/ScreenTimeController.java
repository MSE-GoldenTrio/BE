package com.example.iplan.Controller;

import com.example.iplan.ExceptionHandler.CustomException;
import com.example.iplan.Service.ScreenTimeService;
import com.example.iplan.auth.oauth2.CustomOAuth2UserDetails;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Controller
@RequiredArgsConstructor
@RequestMapping("/screen-time")
public class ScreenTimeController {

    private final ScreenTimeService screenTimeService;

    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadScreenTimeFile(@RequestParam("image")MultipartFile image, @AuthenticationPrincipal CustomOAuth2UserDetails user, @RequestParam(value = "installed_apps", required = false) String installedAppsJson) throws ExecutionException, InterruptedException {
        String childNickname = user.getUsername();

        List<String> installedApps = null;

        if(installedAppsJson != null && !installedAppsJson.isEmpty()){
            try{
                ObjectMapper objectMapper = new ObjectMapper();
                installedApps = objectMapper.readValue(installedAppsJson, new TypeReference<>() {});
            } catch (JsonProcessingException e) {
                throw new CustomException("앱 목록 파싱 실패", HttpStatus.BAD_REQUEST);
            }
        }

        return screenTimeService.uploadScreenTimeImage(image, childNickname, installedApps);
    }

    @GetMapping("/showTimeSet/{targetDate}")
    public ResponseEntity<Map<String, Object>> GetScreenTime(@AuthenticationPrincipal CustomOAuth2UserDetails user, @PathVariable String targetDate) throws ExecutionException, InterruptedException{
        String childNickname = user.getUsername();
        return screenTimeService.getScreenTime(childNickname, targetDate);
    }

    @GetMapping("/showScreenTimeGraph/{targetDate}")
    public ResponseEntity<Map<String, Object>> GetScreenTimeGraph(@AuthenticationPrincipal CustomOAuth2UserDetails user, @PathVariable String targetDate) throws ExecutionException, InterruptedException{
        String childNickname = user.getUsername();
        return screenTimeService.getScreenTimeGraph(childNickname, targetDate);
    }

}
