package com.example.iplan.auth;

import com.example.iplan.Domain.PlanChild;
import com.example.iplan.ExceptionHandler.CustomException;
import com.example.iplan.Repository.FeedbackRepository;
import com.example.iplan.Repository.PlanChildRepository;
import com.example.iplan.Repository.RewardChildRepository;
import com.example.iplan.Repository.PlanChildRepository;
import com.example.iplan.Service.AlarmService;
import com.example.iplan.Service.PlanChildService;
import com.example.iplan.fcm.FcmTokenService;
import com.example.iplan.auth.jwt.JwtProperties;
import com.example.iplan.auth.jwt.JwtToken;
import com.example.iplan.auth.jwt.JwtTokenProvider;
import com.example.iplan.auth.oauth2.CustomOAuth2UserDetails;
import com.example.iplan.auth.redis.RefreshTokenService;
import com.example.iplan.util.AES256Encryptor;
import com.example.iplan.scheduler.PushSchedulerService;
import com.google.api.client.util.Value;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {
    private final Firestore firestore;

    private final UserRepository userRepository;
    private final PlanChildRepository planChildRepository;
    private final RewardChildRepository rewardChildRepository;
    private final FeedbackRepository feedbackRepository;

    private final AuthenticationManagerBuilder authenticationManagerBuilder;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final JwtProperties jwtProperties;

    private final RefreshTokenService refreshTokenService;
    private final FcmTokenService fcmTokenService;
    private final PlanChildService planChildService;
    private final AlarmService alarmService;
    private final PushSchedulerService pushSchedulerService;

    private final AES256Encryptor aes;

    // 회원가입
    public String signUp(String nickname, String password, String name, String email, String roleStr) {
        try {
            // 1. 아이디 중복 확인
            if (nickname != null && userRepository.findByHashValueNickName(DigestUtils.sha256Hex(nickname)).isPresent()) {
                throw new CustomException("Nickname already exists.", HttpStatus.NOT_FOUND);
            }

            UserRole role = UserRole.fromString(roleStr);   // Enum 변환

            // 2. Users 객체 생성
            assert nickname != null;
            Users user = Users.builder()
                    .nickname(aes.encrypt(nickname))
                    .nicknameHash(DigestUtils.sha256Hex(nickname)) // 중복 비교용 해시 추가
                    .email(aes.encrypt(email))
                    .emailHash(DigestUtils.sha256Hex(email))
                    .password(passwordEncoder.encode(password)) // 비밀번호 암호화
                    .name(aes.encrypt(name))
                    .authority(role)    // child, parent
                    .linked_id(new ArrayList<>()) // 빈 리스트로 초기화
                    .build();

            // 3. 사용자 정보 User 컬렉션에 저장 -> 자동 증가된 ID로 저장
            userRepository.saveWithAutoIncrement(user);

            return "Sign Up Successfully";
        } catch (ExecutionException | InterruptedException e) {
            throw new CustomException("Error accessing Firestore: " + e.getMessage(), HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            throw new CustomException(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // 로그인
    public JwtToken signIn(String nickname, String password, String fcmToken) {
        try {
            // 1. 사용자의 입력값으로 UsernamePasswordAuthenticationToken 생성 -> 비밀번호 검증을 위해 사용됨
            UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(nickname, password);
            log.info("Passed signIn 1");

            // AuthenticationManager 가 로그인 요청을 처리 (여기서 사용자 인증과 비밀번호 검증이 이루어짐)
            // 내부적으로 BCryptPasswordEncoder.matches(입력된 비밀번호, 저장된 암호화된 비밀번호)를 실행하여 검증

            // 2. AuthenticationManager.authenticate()가 호출됨
            // 2-1. 여기서 AuthenticationManager 가 CustomUserDetailsService.loadUserByUsername()을 내부적으로 호출
            // -> 디비에서 해당 이메일을 가진 사용자 조회 후 CustomUserDetails 객체 반환
            // 2-2. 이후 AuthenticationManager 가 CustomUserDetails 객체와 위에서 생성한 UsernamePasswordAuthenticationToken 울 바교하여 사용자 인증을 알아서 해줌
            // 2-3. 검증 완료되면 CustomUserDetails 객체를 인증 객체 (Authentication)로 변환하여 인증 완료
            Authentication authentication = authenticationManagerBuilder.getObject().authenticate(authenticationToken);
            log.info("Passed signIn 2");

            // 3. 사용자 인증 이후 Authentication 객체를 SecurityContextHolder 에 저장
            SecurityContextHolder.getContext().setAuthentication(authentication);

            // 인증된 사용자 조회
            Users user = userRepository.findByNickname(nickname)
                    .orElseThrow(() -> new IllegalArgumentException("User not found."));

            // 4. fcmToken 디비에 업데이트
            fcmTokenService.save(nickname, fcmToken);

            // 5. 새 디바이스에 푸시 예약 복구
            saveFutureAlarm(nickname, fcmToken);

            // 6. 인증 객체 (Authentication)을 바탕으로 JWT 토큰 생성
            JwtToken jwtToken = jwtTokenProvider.generateToken(authentication);
            log.info("JwtToken created: accessToken = {}, refreshToken = {}", jwtToken.getAccessToken(), jwtToken.getRefreshToken());

            // 7. Refresh 토큰 Redis 에 저장
            long expirationMinutes = jwtProperties.getRefreshTokenExpiration() / 1000 / 60; // ms → minutes

            refreshTokenService.saveToken(
                    (CustomOAuth2UserDetails) authentication.getPrincipal(),
                    jwtToken.getRefreshToken(),
                    expirationMinutes
            );
            log.info("Saved refresh token in Redis: nickname={}, ttl={}min", nickname, expirationMinutes);

            // 8. jwt 반환
            return jwtToken;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다.");
        }
    }

    public void withdraw(String accessToken, String fcmToken, String userId) throws ExecutionException, InterruptedException {
        String nickname = jwtTokenProvider.getUserNickname(accessToken);
        Users user = userRepository.findByNickname(nickname)
                .orElseThrow(() -> new CustomException("사용자를 찾을 수 없습니다", HttpStatus.NOT_FOUND));

        log.info("회원 탈퇴 사용자 id: {}", nickname);

        // 1. FCM 토큰 삭제
        if (fcmToken != null && !fcmToken.isBlank()) {
            fcmTokenService.deleteToken(userId, fcmToken);
        }

        // 1. 토큰 무효화
        jwtTokenProvider.destroyToken(accessToken, "withdraw");

        // 2. 연동 해제
        for (String linkedId : user.getLinked_id()) {
            deleteLinkedId(user.getEmail(), linkedId);
        }

        // 3. 소셜 연동 해제
        if(user.getProvider() != null && user.getProviderAccessToken() != null){
            try{
                unlinkSocial(user.getProvider(), user.getProviderAccessToken());
            }catch (Exception e){
                log.warn("소셜 연동 해제 실패 : {}", e.getMessage());
                throw new CustomException("소셜 연동 해제 실패", HttpStatus.BAD_REQUEST);
            }
            user.setProviderAccessToken(null); // 토큰 사용 후 파기
        }

        // 4. 관련 데이터 삭제 (계획, 보상 등)
        planChildRepository.deleteAllByUserId(nickname);
        rewardChildRepository.deleteAllByUserId(nickname);
        feedbackRepository.deleteAllByUserId(nickname);
        // 5. 사용자 문서 삭제
        userRepository.delete(user);

        log.info("회원 탈퇴 완료: {}", nickname);
    }

    public void unlinkSocial(String provider, String providerAccessToken){
        if(providerAccessToken == null || providerAccessToken.isBlank()){
            log.warn("소셜 연동 해제 생략: 토큰 없음");
            return;
        }

        try{
            switch (provider.toUpperCase()){
                case "KAKAO":
                    unlinkKakao(providerAccessToken);
                    break;
                case "NAVER":
                    unlinkNaver(providerAccessToken);
                    break;
                case "GOOGLE":
                    unlinkGoogle(providerAccessToken);
                    break;
                default:
                    log.warn("지원하지 않는 소셜 provider입니다: {}", provider);
            }
        }catch(Exception e){
            log.warn("{} 소셜 연동 해제 중 오류 발생: {}", provider, e.getMessage());
            throw new CustomException("소셜 연동 해제 중 오류 발생", HttpStatus.BAD_REQUEST);
        }
    }

    private void unlinkKakao(String accessToken){
        String url = "https://kapi.kakao.com/v1/user/unlink";
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        HttpEntity<?> entity = new HttpEntity<>(headers);
        ResponseEntity<String> response = new RestTemplate().postForEntity(url, entity, String.class);

        if(response.getStatusCode().is2xxSuccessful()){
            log.info("카카오 연동 해제 성공");
        }else{
            log.warn("카카오 연도오 해제 실패 또는 이미 해제됨: {}", response.getBody());
        }
    }
    private void unlinkGoogle(String accessToken) {
        String url = "https://oauth2.googleapis.com/revoke?token=" + accessToken;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        HttpEntity<?> entity = new HttpEntity<>(headers);
        ResponseEntity<String> response = new RestTemplate().postForEntity(url, entity, String.class);

        if (response.getStatusCode().is2xxSuccessful()) {
            log.info("구글 연동 해제 성공");
        } else {
            log.warn("구글 연동 해제 실패 또는 이미 해제됨: {}", response.getBody());
        }
    }

    @Value("${spring.security.oauth2.client.registration.naver.client-id}")
    private String naverClientId;

    @Value("${spring.security.oauth2.client.registration.naver.client-secret}")
    private String naverClientSecret;

    private void unlinkNaver(String accessToken) {
        String url = "https://nid.naver.com/oauth2.0/token" +
                "?grant_type=delete" +
                "&client_id=" + naverClientId +
                "&client_secret=" + naverClientSecret +
                "&access_token=" + accessToken +
                "&service_provider=NAVER";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        HttpEntity<?> entity = new HttpEntity<>(headers);
        ResponseEntity<String> response = new RestTemplate().postForEntity(url, entity, String.class);

        if (response.getStatusCode().is2xxSuccessful()) {
            log.info("네이버 연동 해제 성공");
        } else {
            log.warn("네이버 연동 해제 실패 또는 이미 해제됨: {}", response.getBody());
        }
    }


    /**
     * 닉네임을 기반으로 사용자 조회
     */
    public Users findByNickname(String nickname) {
        Optional<Users> user = userRepository.findByNickname(nickname);
        return user.orElse(null); // 사용자 없을 경우 null 반환
    }

    public Users findByEmail(String email){
        Optional<Users> user = userRepository.findByEmail(email);
        return user.orElse(null);
    }

    public Users findByHashEmail(String email){
        Optional<Users> user = userRepository.findByHashValueNickName(DigestUtils.sha256Hex(email));
        return user.orElse(null);
    }

    /**
     * 아이디(닉네임) 중복 체크
     */
    public boolean isNicknameAvailable(String nickname) {
        Optional<Users> user = userRepository.findByHashValueNickName(DigestUtils.sha256Hex(nickname));
        return user.isEmpty(); // 사용 가능하면 true, 중복이면 false
    }

    /**
     * 이메일 중복 체크
     */
    public boolean isEmailAvailable(String email) {
        Optional<Users> user = userRepository.findByHashValueEmail(email);
        return user.isEmpty(); // 사용 가능하면 true, 중복이면 false
    }

    /**
     * 소셜 로그인 성공 이후 추가 정보(역할) 업데이트
     */
    public void updateUserRole(String nickname, String roleStr) {
        try {
            // 닉네임으로 사용자 조회
            Users user = userRepository.findByNickname(nickname)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found"));

            // 문자열을 UserRole Enum으로 변환
            UserRole role = UserRole.fromString(roleStr);
            user.setAuthority(role); // ✅ 역할 업데이트

            userRepository.update(user);
            log.info("Updated successfully: {}, {}", nickname, role);
        } catch (ExecutionException e) {
            log.error("Firestore ExecutionException Error.. {}", e.getMessage());
            throw new RuntimeException("Firestore 데이터 처리 중 오류 발생", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Firestore InterruptedException Error.. {}", e.getMessage());
            throw new RuntimeException("Firestore 작업이 중단되었습니다.", e);
        } catch (Exception e) {
            log.error("Error..{}", e.getMessage());
            throw new RuntimeException("사용자 역할 업데이트 처리 중 오류 발생", e);
        }
    }

    /**
     * 해당 이메일 사용자를 찾아서 비밀번호를 변경한다.
     * 비밀번호는 암호화처리 후 저장
     * @param encryptEmail
     * @param rawPassword
     * @throws ExecutionException
     * @throws InterruptedException
     */
    public void updatePasswordByEmail(String encryptEmail, String rawPassword) throws ExecutionException, InterruptedException {
        String encoded = passwordEncoder.encode(rawPassword);

        List<QueryDocumentSnapshot> docs = firestore.collection("User")
                .whereEqualTo("encryptEmail", encryptEmail)
                .get()
                .get()
                .getDocuments();

        for (DocumentSnapshot doc : docs) {
            try {
                firestore.collection("User")
                        .document(doc.getId())
                        .update("password", encoded)
                        .get();
            } catch (InterruptedException | ExecutionException e) {
                System.out.println("업데이트 실패: " + e.getMessage());
                throw new CustomException("비밀번호 변경 실패", HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }

    }

    /**
     * 다른 FCM 디바이스에서 새로 로그인할 때,
     * 해당 유저가 알림을 설정한 향후 모든 계획을 새 fcmToken 기준으로 예약
     */
    public void saveFutureAlarm(String userId, String fcmToken) throws ExecutionException, InterruptedException {
        List<PlanChild> futurePlans = planChildService.findFuturePlansWithAlarm(userId);
        log.info("미래 알람 계획 수: {}", futurePlans.size());

        for (PlanChild plan : futurePlans) {
            try {
                pushSchedulerService.schedulePushNotification(plan, fcmToken);
//                alarmService.saveAlarm(plan.getId(), fcmToken);
                log.info("향후 푸시 예약 및 Alarm 저장 완료: plan={}, token={}", plan.getId(), fcmToken);
            } catch (Exception e) {
                log.error("푸시 예약/저장 실패: plan={}, error={}", plan.getId(), e.getMessage(), e);
            }
        }

    }

    /**
     * 계정 연동 해제
     */
    public void deleteLinkedId(String email, String linked_id){
        try{
            // 사용자의 연동된 계정을 삭제하고
            Users user = findByEmail(email);

            List<String> user_linked_id = user.getLinked_id();
            user_linked_id.remove(linked_id);
            user.setLinked_id(user_linked_id);

            userRepository.update(user);

            // 상대 연동 계정에서 나도 삭제한다.
            Users linked_user = userRepository.findByNickname(linked_id)
                    .orElseThrow(() -> new CustomException("사용자를 찾을 수 없습니다: " + linked_id, HttpStatus.NOT_FOUND));

            List<String> linked_user_linked_id = linked_user.getLinked_id();
            linked_user_linked_id.remove(user.getNickname());
            linked_user.setLinked_id(linked_user_linked_id);

            userRepository.update(linked_user);

        }catch (Exception e){
            throw new CustomException("연동 아이디 제거 중 오류 발생: "+ e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }
}

