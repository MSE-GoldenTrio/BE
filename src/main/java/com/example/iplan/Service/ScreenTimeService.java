package com.example.iplan.Service;

import com.example.iplan.DTO.ScreenTimeResultDTO;
import com.example.iplan.Domain.InstalledApps;
import com.example.iplan.Domain.ScreenTime;
import com.example.iplan.Domain.ScreenTimeOCRResult;
import com.example.iplan.ExceptionHandler.CustomException;
import com.example.iplan.Repository.GetScreenTimeOCRRepository;
import com.example.iplan.Repository.InstalledAppsRepository;
import com.example.iplan.Repository.SetScreenTimeRepository;
import com.example.iplan.config.GoogleConfig;
import com.example.iplan.util.AES256Encryptor;
import com.example.iplan.util.SimplePair;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.vision.v1.*;
import com.google.cloud.vision.v1.Image;
import com.google.protobuf.ByteString;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.awt.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Service
@Slf4j
@RequiredArgsConstructor
public class ScreenTimeService {

    private final SetScreenTimeRepository setScreenTimeRepository;
    private final GetScreenTimeOCRRepository getScreenTimeOCRRepository;
    private final InstalledAppsRepository installedAppsRepository;
    private final GoogleConfig googleConfig;
    private final AES256Encryptor aes;

    private static final String KEY_DATE = "date";
    private static final String KEY_MAIN_TIME = "mainTime";
    private static final String KEY_CATEGORIES = "categories";

    private static final Pattern TIME_PATTERN = Pattern.compile("(\\d+)시간 (\\d+)분");
    private static final Pattern MINUTES_PATTERN = Pattern.compile("(\\d+)분");

    private static final Set<String> CATEGORY_TRIGGER_KEYWORDS = Set.of("최다 사용", "카테고리 보기", "많이 사용한 앱");

    public ResponseEntity<Map<String, Object>> getScreenTimeGraph(String user_id, String targetDate) throws Exception {
        Map<String, Object> response = new HashMap<>();

        ScreenTimeOCRResult result = getScreenTimeOCRRepository.findByDate(user_id, targetDate);
        System.out.println("그래프 타겟 날짜: "+ targetDate +", user_id: "+ user_id + ", result: "+ result);

        if(result == null){
            response.put("success", false);
            response.put("message", "해당 날짜의 업로드 된 사진이 없습니다.");

            System.out.println("result null이어서 false response 발송");

            return new ResponseEntity<>(response, HttpStatus.OK);
        }

        ScreenTimeResultDTO resultDTO = ScreenTimeResultDTO.builder()
                        .id(result.getId())
                        .user_id(aes.decrypt(result.getUser_id()))
                        .date(result.getDate())
                        .result(aes.decryptJsonObject(result.getResult(), new TypeReference<Map<String, Object>>() {}))
                        .isSuccess(result.isSuccess())
                        .build();

        response.put("entity", resultDTO);
        response.put("success", true);

        System.out.println("isSuccess?: " + result.isSuccess());

        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    public ResponseEntity<Map<String, Object>> getScreenTime(String user_id, String targetDate) throws Exception {
        Map<String, Object> response = new HashMap<>();

        System.out.println("사용자 아이디: "+user_id+", 날짜: "+targetDate);

        ScreenTime result = setScreenTimeRepository.findByDate(user_id, targetDate);

        if(result == null){
            response.put("success", false);
            response.put("message", "해당 날짜의 설정된 스크린 타임이 없습니다.");

            return new ResponseEntity<>(response, HttpStatus.OK);
        }

        String deadlineTime = result.getDeadLineTime();
        String goalTime = result.getGoalTime();

        response.put("user_id", aes.decrypt(result.getUser_id()));
        response.put("date", targetDate);
        response.put("deadLineTime", deadlineTime);
        response.put("goalTime", goalTime);
        response.put("success", true);

        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    public ResponseEntity<Map<String, Object>> uploadScreenTimeImage(MultipartFile image, String user_id, List<String> installedApps) throws Exception {
        Map<String, Object> response = new HashMap<>();
        InstalledApps savedInstalledApps = installedAppsRepository.findByUserId(user_id);

        if(user_id == null){
            throw new CustomException("로그인 상태를 확인해주세요.", "유저 아이디가 존재하지 않습니다.", HttpStatus.BAD_REQUEST, null);
        }

        if(installedApps != null){
            System.out.println("설치된 어플 목록을 프론트로부터 새로 받았습니다.");
            InstalledApps newInstalledApps = InstalledApps.builder()
                    .user_id(user_id)
                    .installed_apps(aes.encryptJsonObject(installedApps)).build();

            if(savedInstalledApps == null){
                installedAppsRepository.saveWithAutoIncrement(newInstalledApps);
            } else {
                installedAppsRepository.update(newInstalledApps);
            }
            savedInstalledApps = newInstalledApps;
        }

        if(savedInstalledApps != null){
            try {
                // 1. 임시 파일로 저장
                if (image == null || image.isEmpty()) {
                    throw new CustomException("업로드된 파일이 없습니다.", null, HttpStatus.BAD_REQUEST, null);
                }

                String filename = image.getOriginalFilename();
                if (filename == null || filename.trim().isEmpty()) {
                    log.info("파일 이름이 null 또는 비어 있음. UUID로 대체.");
                    filename = UUID.randomUUID() + ".jpg";
                }

                Path tempDir = Files.createTempDirectory("upload");
                System.out.println("Temporary directory created: " + tempDir);

                Path filePath = tempDir.resolve(filename);
                System.out.println("File will be saved at: " + filePath);

                // 파일 저장
                try {
                    Files.write(filePath, image.getBytes());
                    System.out.println("File written successfully to: " + filePath);
                } catch (IOException e) {
                    throw new CustomException("파일 저장 중 오류 발생", e.toString(), HttpStatus.INTERNAL_SERVER_ERROR, e);
                }

                // 2. 파일 생성 시간 확인(캡쳐 시간 확인)
                BasicFileAttributes attr = Files.readAttributes(filePath, BasicFileAttributes.class);
                FileTime creationTime = attr.creationTime();
                LocalDateTime creationDateTime = creationTime
                        .toInstant()
                        .atZone(ZoneId.systemDefault())
                        .toLocalDateTime();

                System.out.println("파일 생성 시간: " + creationDateTime);

                // 파일 생성 날짜가 오늘이고, 마감 시간을 넘겼을 경우에만 OCR 수행
                if (IsInValidScreenShot(user_id, creationDateTime)) {
                    try {
                        // 3. Google Vision API를 사용하여 OCR 수행
                        List<String> extractedTexts = extractTextFromImage(filePath);

                        // 커스텀 필터를 통해 필요한 텍스트만 추출
                        Map<String, Object> filteredTexts = filterExtractedTexts(extractedTexts, aes.decryptJsonObject(savedInstalledApps.getInstalled_apps(), new TypeReference<List<String>>() {}));

                        // 해당 날짜에 설정해둔 목표 시간에 달성했을 때, 결과물을 담아서 보낸다.
                        boolean screenTimeGoalResult = IsAchieveUsingTime(user_id, filteredTexts);

                        // 현재 날짜
                        LocalDate today = LocalDate.now();
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                        String todayToString = today.format(formatter);

                        // OCR 결과 저장
                        ScreenTimeOCRResult result = ScreenTimeOCRResult.builder()
                                .user_id(user_id)
                                .date(todayToString)
                                .result(aes.encryptJsonObject(filteredTexts))
                                .isSuccess(screenTimeGoalResult)
                                .build();

                        getScreenTimeOCRRepository.saveWithAutoIncrement(result);
                        System.out.println("프론트로 보내는 필터링 된 텍스트" + filteredTexts);
                        response.put("entity", filteredTexts);
                        response.put("success", screenTimeGoalResult);
                        System.out.println("OCR 결과 저장 완료.");

                    } catch (Exception e) {
                        DeleteFolderFiles(filePath);
                        log.error("OCR 처리 중 내부 오류 발생", e);
                        throw new CustomException("OCR 처리 중 내부 오류 발생", e.toString(), HttpStatus.BAD_REQUEST, e);
                    }
                } else {
                    DeleteFolderFiles(filePath);
                    throw new CustomException("오늘 캡쳐된 사진이 아니거나 마감 시간이 지나지 않았습니다.", null, HttpStatus.BAD_REQUEST, null);
                }

                // 마지막에 임시 파일 삭제
                DeleteFolderFiles(filePath);
                return new ResponseEntity<>(response, HttpStatus.OK);

            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
                e.printStackTrace();
                throw new CustomException("일시적 오류가 발생하였습니다.", e.toString(), HttpStatus.INTERNAL_SERVER_ERROR, e);
            }
        }else{
            throw new CustomException("일시적 오류가 발생하였습니다.","사용자 설치 앱 목록을 가져오는데에 실패했습니다.", HttpStatus.NOT_FOUND,null );
        }
    }

    private void DeleteFolderFiles(Path filePath) throws IOException {
        // 'try-with-resources' 구문을 사용하여 자동으로 Stream을 닫기
        try (Stream<Path> paths = Files.walk(filePath.getParent())) {
            // 역순으로 정렬하여 파일부터 삭제
            paths.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                            System.out.println("삭제된 파일: " + path);
                        } catch (IOException e) {
                            throw new CustomException("파일 삭제 중 오류 발생", e.toString(), HttpStatus.BAD_REQUEST, e);
                        }
                    });
            System.out.println("모든 파일 삭제 완료.");
        }
    }


    private List<String> extractTextFromImage(Path imagePath) throws IOException {
        ByteString imgBytes = ByteString.readFrom(Files.newInputStream(imagePath));
        Image img = Image.newBuilder().setContent(imgBytes).build();
        Feature feat = Feature.newBuilder().setType(Feature.Type.TEXT_DETECTION).build();
        AnnotateImageRequest request = AnnotateImageRequest.newBuilder().addFeatures(feat).setImage(img).build();

        System.out.println("googleConfig: " + googleConfig); // null이면 끝
        System.out.println("googleConfig.getCredentials(): " + googleConfig.getCredentials());

        try (ImageAnnotatorClient client = ImageAnnotatorClient.create(
                ImageAnnotatorSettings.newBuilder()
                        .setCredentialsProvider(() ->
                                GoogleCredentials.fromStream(new FileInputStream(googleConfig.getCredentials()))

                        ).build()
        )) {
            AnnotateImageResponse response = client.batchAnnotateImages(
                    java.util.Collections.singletonList(request)).getResponses(0);

            if (response.hasError()) {
                System.out.printf("Error: %s%n", response.getError().getMessage());
                return List.of("사진 OCR 중 오류 발생");
            }

            List<EntityAnnotation> annotations = response.getTextAnnotationsList();
            if (annotations.size() < 2) return List.of();

            List<String> lines = Arrays.stream(annotations.get(0).getDescription().split("\n")).toList();

            List<SimplePair<String, Point>> textWithPositions = new ArrayList<>();

            int index = 1;
            for (String line : lines) {
                int j = index;
                String sumText = annotations.get(j).getDescription();
                List<Vertex> allVertices = new ArrayList<>(annotations.get(j).getBoundingPoly().getVerticesList());

                while (j + 1 < annotations.size() && !line.replaceAll("\\s+", "").equals(sumText)) {
                    j++;
                    sumText += annotations.get(j).getDescription();
                    allVertices.addAll(annotations.get(j).getBoundingPoly().getVerticesList());
                }

                int centerX = (int) allVertices.stream().mapToInt(Vertex::getX).average().orElse(0);
                int centerY = (int) allVertices.stream().mapToInt(Vertex::getY).average().orElse(0);

                textWithPositions.add(new SimplePair<>(line, new Point(centerX, centerY)));
                index = j + 1;
            }

            textWithPositions.sort((p1, p2) -> {
                Point pt1 = p1.getRight();
                Point pt2 = p2.getRight();
                if (Math.abs(pt1.y - pt2.y) > 20) return Integer.compare(pt1.y, pt2.y);
                return Integer.compare(pt1.x, pt2.x);
            });

            List<String> sortedTexts = textWithPositions.stream()
                    .map(SimplePair::getLeft)
                    .toList();

            System.out.println("=========== 최종 정렬된 텍스트 ===========");
            sortedTexts.forEach(t -> System.out.println("text: " + t));
            return sortedTexts;

        } catch (Exception e) {
            System.out.println("OCR 처리 중 예외 발생: " + e);
            throw new CustomException("OCR 처리 중 오류가 발생했습니다.", e.toString(), HttpStatus.BAD_REQUEST, e);
        }
    }

    public Map<String, Object> filterExtractedTexts(List<String> extractedTexts, List<String> savedInstalledApps) {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, String>> categories = new ArrayList<>();

        boolean mainTimeCaptured = false;
        boolean isCategorySection = false;
        AtomicInteger timeCount = new AtomicInteger(0);

        try{
            result.put(KEY_DATE, LocalDate.now().toString());

            for (String text : extractedTexts) {
                if (!mainTimeCaptured && TIME_PATTERN.matcher(text).matches()) {
                    result.put(KEY_MAIN_TIME, TimeFormatter(TIME_PATTERN.matcher(text)));
                    mainTimeCaptured = true;
                    timeCount.incrementAndGet();
                    continue;
                }

                if (CATEGORY_TRIGGER_KEYWORDS.contains(text)) {
                    isCategorySection = true;
                    continue;
                }

                if (mainTimeCaptured && isCategorySection) {
                    processCategoryInfo(text, timeCount, categories, savedInstalledApps);
                }
            }

            if(categories.isEmpty()){
                log.info("사용자 기기에 추가되지 않은 어플이 있습니다.");
                throw new CustomException("사진과 사용자 기기의 정보가 일치하지 않습니다. 올바른 사진을 올려주세요.", null, HttpStatus.BAD_REQUEST, null);
            }
            result.put(KEY_CATEGORIES, categories);
            return result;
        }catch(Exception e){
            System.out.println("Error: " + e.getMessage());
            e.printStackTrace();
            throw new CustomException("추출된 텍스트 분석중 오류가 발생하였습니다.", e.toString(), HttpStatus.BAD_REQUEST, e);
        }
    }

    private void processCategoryInfo(String text, AtomicInteger timeCount, List<Map<String, String>> categories, List<String> savedInstalledApps) {
        try {
            if (timeCount.get() > 3) return;

            Matcher timeMatcher = TIME_PATTERN.matcher(text);
            Matcher minutesMatcher = MINUTES_PATTERN.matcher(text);

            if (timeMatcher.matches() || minutesMatcher.matches()) {
                String time = timeMatcher.matches() ? TimeFormatter(timeMatcher) : TimeFormatter(minutesMatcher);
                if (timeCount.get() - 1 < categories.size()) {
                    categories.get(timeCount.get() - 1).put("time", time);
                    timeCount.incrementAndGet();
                }
            } else if (isValidAppName(text) && savedInstalledApps.contains(text)) {
                Map<String, String> category = new HashMap<>();
                category.put("name", text);
                categories.add(category);
            }
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
            throw new CustomException("그래프 분석 중 오류가 발생하였습니다.", e.toString(), HttpStatus.BAD_REQUEST, e);
        }
    }

    private boolean isValidAppName(String text) {
        return text != null && text.length() > 1 && text.matches(".*[가-힣a-zA-Z0-9].*");
    }

    private boolean IsInValidScreenShot(String user_id, LocalDateTime fileCreationDateTime) throws ExecutionException, InterruptedException {
        String fileCreationDate = fileCreationDateTime.toLocalDate().toString();
        String fileCreationTime = fileCreationDateTime.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm:ss"));

        var screenTimeData = setScreenTimeRepository.findByDate(user_id, LocalDate.now().toString());
        System.out.println(LocalDate.now());
        if(screenTimeData == null){
            throw new CustomException("스크린 타임 시간이 설정되지 않았습니다!", null, HttpStatus.BAD_REQUEST, null);
        }

        String deadLineTime = screenTimeData.getDeadLineTime();
        System.out.println("사진 올리는 마감 시간 : " + deadLineTime);

        return fileCreationDate.equals(LocalDate.now().toString()) && LocalTime.parse(fileCreationTime).isAfter(LocalTime.parse(deadLineTime));
    }

    private boolean IsAchieveUsingTime(String user_id, Map<String, Object> filteredTexts) throws ExecutionException, InterruptedException {
        LocalTime mainTime = LocalTime.parse(filteredTexts.get("mainTime").toString(), DateTimeFormatter.ofPattern("HH:mm"));
        Duration mainTimeDuration = Duration.between(LocalTime.MIDNIGHT, mainTime);

        String goalTimeString = setScreenTimeRepository.findByDate(user_id, LocalDate.now().toString()).getGoalTime();
        LocalTime goalTime = LocalTime.parse(goalTimeString, DateTimeFormatter.ofPattern("HH:mm"));
        Duration goalTimeDuration = Duration.between(LocalTime.MIDNIGHT, goalTime);

        return mainTimeDuration.compareTo(goalTimeDuration) < 0;
    }

    private String TimeFormatter(Matcher matcher) {
        if (!matcher.matches()) {
            throw new CustomException("일시적 오류가 발생하였습니다.", "No Match Found: 그래프 분석 TimeFormatter", HttpStatus.BAD_REQUEST,null );
        }

        LocalTime parseTime = LocalTime.MIDNIGHT;

        if (matcher.groupCount() == 2) {
            int hours = Integer.parseInt(matcher.group(1));
            int minutes = Integer.parseInt(matcher.group(2));
            parseTime = LocalTime.of(hours, minutes);
        } else if (matcher.groupCount() == 1) {
            int minutes = Integer.parseInt(matcher.group(1));
            parseTime = LocalTime.of(0, minutes);
        }

        return parseTime.format(DateTimeFormatter.ofPattern("HH:mm"));
    }

}
