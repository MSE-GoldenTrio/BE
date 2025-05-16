package com.example.iplan.Controller;

import com.example.iplan.Service.ScreenTimeService;
import com.example.iplan.auth.oauth2.CustomOAuth2UserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Controller
@RequiredArgsConstructor
@RequestMapping("/screen-time")
public class ScreenTimeController {

    private final ScreenTimeService screenTimeService;

    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadScreenTimeFile(@RequestParam("image")MultipartFile image, @AuthenticationPrincipal CustomOAuth2UserDetails user) throws IOException, ExecutionException, InterruptedException {
        String childNickname = user.getUsername();
        return screenTimeService.uploadScreenTimeImage(image, childNickname);
    }

    @GetMapping("/showTimeSet/{targetDate}")
    public ResponseEntity<Map<String, Object>> GetScreenTime(@AuthenticationPrincipal String user_id, @PathVariable String targetDate) throws ExecutionException, InterruptedException{
        return screenTimeService.getScreenTime(user_id, targetDate);
    }

    @GetMapping("/showScreenTimeGraph/{targetDate}")
    public ResponseEntity<Map<String, Object>> GetScreenTimeGraph(@AuthenticationPrincipal String user_id, @PathVariable String targetDate) throws ExecutionException, InterruptedException{
        return screenTimeService.getScreenTimeGraph(user_id, targetDate);
    }

}
