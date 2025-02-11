package com.example.iplan.Controller;

import com.example.iplan.Service.ScreenTimeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    public ResponseEntity<Map<String, Object>> uploadScreenTimeFile(@RequestParam("image")MultipartFile image, @AuthenticationPrincipal String user_id) throws IOException, ExecutionException, InterruptedException {
        return screenTimeService.uploadScreenTimeImage(image, user_id);
    }

}
