package com.example.iplan.Controller;

import com.example.iplan.ExceptionHandler.CustomException;
import com.example.iplan.Service.ParentScreenTimeService;
import com.example.iplan.auth.UserRepository;
import com.example.iplan.auth.Users;
import com.example.iplan.auth.oauth2.CustomOAuth2UserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Map;
import java.util.concurrent.ExecutionException;

@Controller
@RequiredArgsConstructor
@RequestMapping("/parent/screen-time")
public class ParentScreenTimeController {

    private final ParentScreenTimeService parentScreenTimeService;

    @GetMapping("/showTimeSet/{targetDate}")
    public ResponseEntity<Map<String, Object>> GetScreenTime(@AuthenticationPrincipal CustomOAuth2UserDetails user, @PathVariable String targetDate) throws ExecutionException, InterruptedException{
        System.out.println("User ID: " + user.getUsername() + ", Target Date: " + targetDate);

        return parentScreenTimeService.getScreenTime(user.getUsername(), targetDate);
    }

    @GetMapping("/showScreenTimeGraph/{targetDate}")
    public ResponseEntity<Map<String, Object>> GetScreenTimeGraph(@AuthenticationPrincipal CustomOAuth2UserDetails user, @PathVariable String targetDate) throws Exception {
        System.out.println("User ID: " + user.getUsername() + ", Target Date: " + targetDate );

        return parentScreenTimeService.getScreenTimeGraph(user.getUsername(), targetDate);
    }
}
