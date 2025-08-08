package com.example.iplan.auth;

import com.example.iplan.Domain.PlanChild;
import com.example.iplan.ExceptionHandler.CustomException;
import com.example.iplan.Repository.FeedbackRepository;
import com.example.iplan.Repository.PlanChildRepository;
import com.example.iplan.Repository.RewardChildRepository;
import com.example.iplan.Service.AlarmService;
import com.example.iplan.Service.PlanChildService;
import com.example.iplan.fcm.FcmRequestDTO;
import com.example.iplan.fcm.FcmRequestService;
import com.example.iplan.fcm.FcmToken;
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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.http.*;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
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
    private final FcmRequestService fcmRequestService;

    private final AES256Encryptor aes;

    // 회원가입
    public String signUp(String nickname, String password, String name, String email, String roleStr) {
        try {
            // 1. 아이디 중복 확인 (닉네임 해시값으로 중복 비교)
            if (nickname != null && userRepository.findByHashValueNickName(DigestUtils.sha256Hex(nickname)).isPresent()) {
                throw new CustomException("이미 존재하는 아이디입니다.", null, HttpStatus.NOT_FOUND, null);
            }

            // 2. authority Enum 변환
            UserRole role = UserRole.fromString(roleStr);

            assert nickname != null;
            // 3. Users 객체 생성
            Users user = Users.builder()
                    .nickname(aes.encrypt(nickname))
                    .nicknameHash(DigestUtils.sha256Hex(nickname)) // 중복 비교용 해시 추가
                    .email(aes.encrypt(email))
                    .emailHash(DigestUtils.sha256Hex(email))
                    .password(passwordEncoder.encode(password)) // 비밀번호 암호화
                    .name(aes.encrypt(name))
                    .authority(role)    // child, parent
                    .linked_id(new ArrayList<>()) // 빈 리스트로 초기화
                    .firebaseAuthUID("")
                    .build();

            // 4. 사용자 정보 User 컬렉션에 저장 -> 자동 증가된 ID로 저장
            userRepository.saveWithAutoIncrement(user);
            log.info("회원가입 성공");

            return "Sign Up Successfully";
        } catch(CustomException ce){
            throw ce;
        } catch(ExecutionException | InterruptedException e) {
            throw new CustomException("일시적 오류가 발생하였습니다.", "Firestore 접근 오류: " + e, HttpStatus.BAD_REQUEST, e);
        } catch (Exception e) {
            throw new CustomException("일시적 오류가 발생하였습니다.", e.toString(), HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
    }

    /**
     * 로그인
     * @param user_id 헷갈려서 user_id로 해놓음 User테이블의 nickname임
     * @param password
     * @param fcmToken
     * @return
     */
    public JwtToken signIn(String user_id, String password, String fcmToken) {
        try {
            // 1. 사용자 입력으로 UsernamePasswordAuthenticationToken 생성
            UsernamePasswordAuthenticationToken authenticationToken =
                    new UsernamePasswordAuthenticationToken(user_id, password);
            log.info("Passed signIn 1");

            // 2. 사용자 인증 시도
            Authentication authentication;
            try {
                authentication = authenticationManagerBuilder.getObject().authenticate(authenticationToken);
                log.info("Passed signIn 2");
            } catch (BadCredentialsException e) {
                // 비밀번호 틀림
                throw new CustomException("비밀번호가 잘못 입력되었습니다.", null, HttpStatus.UNAUTHORIZED, e);
            } catch (UsernameNotFoundException e) {
                // 사용자 존재하지 않음
                throw new CustomException("존재하지 않는 사용자입니다. 회원가입을 진행해주세요.", null, HttpStatus.NOT_FOUND, e);
            } catch (Exception e) {
                // 기타 인증 관련 오류
                throw new CustomException("로그인 인증 과정에서 오류가 발생했습니다.", e.getMessage(), HttpStatus.UNAUTHORIZED, e);
            }

            // 3. 인증 성공 시 SecurityContext 에 저장
            SecurityContextHolder.getContext().setAuthentication(authentication);

            // 4. 사용자 정보 조회 (닉네임 해시 기준)
            Users user = userRepository.findByHashValueNickName(DigestUtils.sha256Hex(user_id))
                    .orElseThrow(() -> new CustomException("잘못된 아이디입니다.", null, HttpStatus.NOT_FOUND, null));

            // 5. Firebase 사용자 UID 등록
            if (user.getFirebaseAuthUID() == null || user.getFirebaseAuthUID().isBlank()) {
                try {
                    UserRecord userRecord;
                    try {
                        userRecord = FirebaseAuth.getInstance().getUserByEmail(aes.decrypt(user.getEmail()));
                        log.info("기존 Firebase 사용자: {}", userRecord.getUid());
                    } catch (Exception e) {
                        UserRecord.CreateRequest createRequest = new UserRecord.CreateRequest()
                                .setEmail(aes.decrypt(user.getEmail()))
                                .setDisplayName(aes.decrypt(user.getName()));
                        userRecord = FirebaseAuth.getInstance().createUser(createRequest);
                        log.info("새 Firebase 사용자 생성됨: {}", userRecord.getUid());

                        user.setFirebaseAuthUID(userRecord.getUid());
                        userRepository.update(user);
                    }
                } catch (Exception e) {
                    log.error("Firebase 사용자 생성 중 오류", e);
                    throw new CustomException("Firebase 사용자 생성 중 오류", e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, e);
                }
            }

            // 6. UID 포함한 Authentication 객체 다시 설정
            CustomOAuth2UserDetails updatedUserDetails = new CustomOAuth2UserDetails(user);
            Authentication updatedAuth = new UsernamePasswordAuthenticationToken(
                    updatedUserDetails, null, updatedUserDetails.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(updatedAuth);

            // 7. fcmToken 저장
            fcmTokenService.save(user.getNicknameHash(), fcmToken);

            // 8. 푸시 알람 예약 복구
            saveFutureAlarm(user.getNickname(), fcmToken);

            // 9. JWT 생성
            JwtToken jwtToken = jwtTokenProvider.generateToken(updatedAuth);
            log.info("JWT 생성 완료: access = {}, refresh = {}", jwtToken.getAccessToken(), jwtToken.getRefreshToken());

            // 10. Refresh 토큰 Redis 저장
            long expirationMinutes = jwtProperties.getRefreshTokenExpiration() / 1000 / 60;
            refreshTokenService.saveToken(
                    (CustomOAuth2UserDetails) authentication.getPrincipal(),
                    jwtToken.getRefreshToken(),
                    expirationMinutes
            );
            log.info("Refresh 토큰 저장 완료: user_id={}, TTL={}min", user_id, expirationMinutes);

            return jwtToken;

        } catch (CustomException ce) {
            throw ce;
        } catch (Exception e) {
            throw new CustomException("로그인 중 알 수 없는 오류가 발생했습니다.", e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
    }


    /**
     * 계정 탈퇴
     * @param accessToken
     * @param fcmToken
     * @param encryptedUserId
     */
    public String withdraw(String accessToken, String fcmToken, String encryptedUserId) throws ExecutionException, InterruptedException {
        String nickname = jwtTokenProvider.getUserNickname(accessToken);
        Users user = userRepository.findByHashValueNickName(DigestUtils.sha256Hex(nickname))
                .orElseThrow(() -> new CustomException("사용자를 찾을 수 없습니다", null, HttpStatus.NOT_FOUND, null));
        String uid = user.getFirebaseAuthUID();
        log.info("계정 탈퇴 사용자 nickname: {}, Firebase uid: {}", nickname, uid);

        // 1. FCM 토큰 삭제
        if (fcmToken != null && !fcmToken.isBlank()) {
            fcmTokenService.deleteToken(user.getNicknameHash(), fcmToken);
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
                throw new CustomException("소셜 연동 해제 실패", e.toString(), HttpStatus.BAD_REQUEST,e );
            }
            user.setProviderAccessToken(null); // 토큰 사용 후 파기
        }

        // 4. 관련 데이터 삭제 (계획, 보상, 피드백)
        planChildRepository.deleteAllByUserId(encryptedUserId);
        rewardChildRepository.deleteAllByUserId(encryptedUserId);
        feedbackRepository.deleteAllByUserId(encryptedUserId);
        log.info("관련 데이터 삭제 완료");

        // 5. 유저 삭제
        userRepository.delete(user);
        log.info("유저 삭제 완료");

        // 6. Firebase Authentication 사용자 삭제
        try {
            UserRecord userRecord = FirebaseAuth.getInstance().getUser(uid);
            log.info("삭제 대상 Firebase 사용자 이메일: {}", userRecord.getEmail());
            FirebaseAuth.getInstance().deleteUser(uid);
            log.info("Firebase 사용자 삭제 완료: {}", uid);
        } catch (FirebaseAuthException e) {
            log.error("Firebase 사용자 삭제 실패: {}", e.getMessage());
            throw new CustomException("회원 탈퇴 실패", "Firebase 사용자 삭제 실패: " + e, HttpStatus.INTERNAL_SERVER_ERROR,e );
        }

        log.info("회원 탈퇴 완료: {}", nickname);
        return "Delete User Successfully";
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
            throw new CustomException("소셜 연동 해제 실패", e.toString(), HttpStatus.BAD_REQUEST, e);
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
    public Users findByEncryptedNickname(String nickname) {
        Optional<Users> user = userRepository.findByEncryptedNickname(nickname);
        return user.orElse(null); // 사용자 없을 경우 null 반환
    }

    public Users findByHashNickname(String nickname){
        Optional<Users> user = userRepository.findByEncryptedNickname(nickname);
        return user.orElse(null); // 사용자 없을 경우 null 반환
    }

    public Users findByEncryptedEmail(String encryptedEmail){
        Optional<Users> user = userRepository.findByEncryptedEmail(encryptedEmail);
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
     * 소셜 로그인 성공 이후 추가 정보(역할과 uid) 업데이트
     */
    public void updateUserRole(String nickname, String roleStr) {
        try {
            // 닉네임으로 사용자 조회
            Users user = userRepository.findByEncryptedNickname(nickname)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found"));

            // 문자열을 UserRole Enum 으로 변환
            UserRole role = UserRole.fromString(roleStr);
            // 역할 업데이트
            user.setAuthority(role);
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
                .whereEqualTo("email", encryptEmail)
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
                throw new CustomException("비밀번호 변경 실패", e.toString(), HttpStatus.INTERNAL_SERVER_ERROR, e);
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
     * 계정 연동 해제(계정 탈퇴 시에도 쓰임)
     */
    public void deleteLinkedId(String encryptedEmail, String encryptedLinkedId){
        log.info("입력된 encryptedEmail: {}", encryptedEmail);
        log.info("입력된 encryptedLinkedId: {}", encryptedLinkedId);

        try{
            // 1. 계정 연동 해제를 요청한(혹은 계정 탈퇴하는) 유저 조회
            Users request_user = findByEncryptedEmail(encryptedEmail);
            log.info("연동을 해제를 요청하는 유저의 닉네임: {}", aes.decrypt(request_user.getNickname()));

            // 부모의 연동된 계정을 삭제하고
            List<String> request_user_linked_id = request_user.getLinked_id(); // 암호화된 id들
            request_user_linked_id.remove(encryptedLinkedId);
            request_user.setLinked_id(request_user_linked_id);

            userRepository.update(request_user);

            // 2. (requestUser 와 연동된) linked_id 에 해당하는 유저 조회
            Users linked_user = findByEncryptedNickname(encryptedLinkedId);

            // 아이 연동 계정에서 나도 삭제한다.
            List<String> linked_user_linked_id = linked_user.getLinked_id();
            linked_user_linked_id.remove(request_user.getNickname());
            linked_user.setLinked_id(linked_user_linked_id);

            userRepository.update(linked_user);
            log.info("linked_id 삭제 완료");

            // 연동 해제 되었다는 알림을 보낼 상대의 FcmToken찾기
            List<FcmToken> fcmTokens = fcmTokenService.getTokensByHashedUserId(DigestUtils.sha256Hex(aes.decrypt(encryptedLinkedId)));

            log.info("연동 해제하는 상대의 nickname {}", aes.decrypt(encryptedLinkedId));
            try{
                if(!fcmTokens.isEmpty()){
                    for(FcmToken fcmToken : fcmTokens){
                        FcmRequestDTO requestDto = FcmRequestDTO.builder()
                                .user_id(fcmToken.getUser_id())
                                .fcmToken(fcmToken.getToken())
                                .notification(FcmRequestDTO.Notification.builder()
                                        .title("iPlan")
                                        .body(aes.decrypt(request_user.getNickname()) + "과(와)의 연동이 해제되었습니다.")
                                        .build())
                                .data(FcmRequestDTO.Data.builder()
                                        .pendingRequestId(null)
                                        .sender(aes.decrypt(request_user.getNickname()))
                                        .type("DeleteLinkedId")
                                        .build())
                                .build();
                        fcmRequestService.sendPush(requestDto);
                        log.info("연동 해제 알람을 성공적으로 보냈습니다.");
                    }
                }else{
                    log.warn("연동 요청을 보낼 유저({})의 FcmToken이 존재하지 않습니다.", aes.decrypt(encryptedLinkedId));
                }
            }catch (Exception e){
                throw new CustomException("연동 아이디 제거 실패", e.toString(), HttpStatus.INTERNAL_SERVER_ERROR, e);
            }

        }catch (Exception e){
            throw new CustomException("연동 아이디 제거 실패", e.toString(), HttpStatus.BAD_REQUEST, e);
        }
    }

    /**
     * 암호화된 닉네임(encryptedNickname)을 기반으로 CustomOAuth2UserDetails 반환
     */
    public CustomOAuth2UserDetails loadUserByEncryptedNickname(String encryptedNickname) {
        try {
            Optional<Users> optionalUser = userRepository.findByEncryptedNickname(encryptedNickname);
            if (optionalUser.isEmpty()) {
                throw new UsernameNotFoundException("해당 암호화된 닉네임의 사용자를 찾을 수 없습니다.");
            }

            Users user = optionalUser.get();
            return new CustomOAuth2UserDetails(user);
        } catch (Exception e) {
            log.error("loadUserByEncryptedNickname 오류: {}", e.getMessage());
            throw new CustomException("사용자 정보 불러오기 실패", e.toString(), HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
    }

}

