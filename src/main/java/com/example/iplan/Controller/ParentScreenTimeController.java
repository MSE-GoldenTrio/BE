package com.example.iplan.Controller;

import com.example.iplan.Service.ParentScreenTimeService;
import com.example.iplan.auth.oauth2.CustomOAuth2UserDetails;
import com.example.iplan.util.AES256Encryptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.ExecutionException;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/parent/screen-time")
public class ParentScreenTimeController {

    private final ParentScreenTimeService parentScreenTimeService;
    private final AES256Encryptor aes;

    @GetMapping("/showTimeSet/{targetDate}")
    public ResponseEntity<Map<String, Object>> GetScreenTime(@AuthenticationPrincipal CustomOAuth2UserDetails user, @PathVariable String targetDate) throws Exception {
        log.info("User ID: " + aes.decrypt(user.getUsername()) + ", Target Date: " + targetDate);

        return parentScreenTimeService.getScreenTime(user.getUsername(), targetDate);
    }

    @GetMapping("/showScreenTimeGraph/{targetDate}")
    public ResponseEntity<Map<String, Object>> GetScreenTimeGraph(@AuthenticationPrincipal CustomOAuth2UserDetails user, @PathVariable String targetDate) throws Exception {
        log.info("User ID: " + aes.decrypt(user.getUsername()) + ", Target Date: " + targetDate );

        return parentScreenTimeService.getScreenTimeGraph(user.getUsername(), targetDate);
    }

    @GetMapping("/time-set/detection/add")
    public ResponseEntity<Map<String, Object>> GetOneTimeData(@AuthenticationPrincipal CustomOAuth2UserDetails user, @RequestParam("docId") String docId, @RequestParam("childId") String childId) throws Exception {
        String parentEncryptedNickname = user.getUsername();
        log.info("User Id:" + aes.decrypt(parentEncryptedNickname) + ", Target Time Id: " + docId);
        Map<String, Object> response = parentScreenTimeService.getOneTimeData(parentEncryptedNickname, docId, childId);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @GetMapping("/graph/detection/add")
    public ResponseEntity<Map<String, Object>> GetOneGraphData(@AuthenticationPrincipal CustomOAuth2UserDetails user, @RequestParam("graphId") String graphId, @RequestParam("childId") String childId) throws Exception {
        String parentEncryptedNickname = user.getUsername();
        log.info("User ID: " + aes.decrypt(parentEncryptedNickname) + ", Target Graph Id: " + graphId);
        Map<String, Object> response = parentScreenTimeService.getOneGraphData(parentEncryptedNickname, graphId, childId);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}
