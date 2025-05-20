package com.example.iplan.Service;

import com.example.iplan.Domain.InstalledApps;
import com.example.iplan.Domain.ScreenTime;
import com.example.iplan.Domain.ScreenTimeOCRResult;
import com.example.iplan.ExceptionHandler.CustomException;
import com.example.iplan.Repository.GetScreenTimeOCRRepository;
import com.example.iplan.Repository.InstalledAppsRepository;
import com.example.iplan.Repository.SetScreenTimeRepository;
import com.example.iplan.util.SimplePair;
import com.google.cloud.vision.v1.*;
import com.google.cloud.vision.v1.Image;
import com.google.protobuf.ByteString;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.awt.*;
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
@RequiredArgsConstructor
public class ScreenTimeService {

    private final SetScreenTimeRepository setScreenTimeRepository;

    private final GetScreenTimeOCRRepository getScreenTimeOCRRepository;

    private final InstalledAppsRepository installedAppsRepository;

    public ResponseEntity<Map<String, Object>> getScreenTimeGraph(String user_id, String targetDate) throws ExecutionException, InterruptedException{
        Map<String, Object> response = new HashMap<>();

        ScreenTimeOCRResult result = getScreenTimeOCRRepository.findByDate(user_id, targetDate);
        System.out.println("그래프 타겟 날짜: "+ targetDate +", user_id: "+ user_id + ", result: "+ result);

        if(result == null){
            response.put("success", false);
            response.put("message", "해당 날짜의 업로드 된 사진이 없습니다.");

            System.out.println("result null이어서 false response 발송");

            return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
        }
        response.put("entity", result);
        response.put("success", true);

        System.out.println("isSuccess?: " + result.isSuccess());

        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    public ResponseEntity<Map<String, Object>> getScreenTime(String user_id, String targetDate) throws ExecutionException, InterruptedException {
        Map<String, Object> response = new HashMap<>();

        System.out.println("사용자 아이디: "+user_id+", 날짜: "+targetDate);

        ScreenTime result = setScreenTimeRepository.findByDate(user_id, targetDate);

        if(result == null){
            response.put("success", false);
            response.put("message", "해당 날짜의 설정된 스크린 타임이 없습니다.");

            return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
        }

        String deadlineTime = result.getDeadLineTime();
        String goalTime = result.getGoalTime();

        response.put("date", targetDate);
        response.put("deadLineTime", deadlineTime);
        response.put("goalTime", goalTime);
        response.put("success", true);

        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    public ResponseEntity<Map<String, Object>> uploadScreenTimeImage(@RequestParam("file") MultipartFile image, String user_id, List<String> installedApps) throws ExecutionException, InterruptedException {
        Map<String, Object> response = new HashMap<>();
        InstalledApps savedInstalledApps = installedAppsRepository.findByUserId(user_id);

        if(user_id == null){
            throw new CustomException("유저 상태를 확인해주세요.", HttpStatus.BAD_REQUEST);
        }

        if(installedApps != null){
            System.out.println("설치된 어플 목록을 프론트로부터 새로 받았습니다.");
            InstalledApps newInstalledApps = InstalledApps.builder()
                    .user_id(user_id)
                    .installed_apps(installedApps).build();

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
                String filename = image.getOriginalFilename();
                if (filename == null || filename.isEmpty()) {
                    throw new CustomException("파일 이름이 비어 있습니다.", HttpStatus.BAD_REQUEST);
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
                    throw new CustomException("파일 저장 중 오류 발생", HttpStatus.INTERNAL_SERVER_ERROR);
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
                        Map<String, Object> filteredTexts = filterExtractedTexts(extractedTexts, savedInstalledApps.getInstalled_apps());

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
                                .result(filteredTexts)
                                .isSuccess(screenTimeGoalResult)
                                .build();

                        getScreenTimeOCRRepository.saveWithAutoIncrement(result);
                        response.put("entity", filteredTexts);
                        System.out.println("OCR 결과 저장 완료.");

                    } catch (Exception e) {
                        DeleteFolderFiles(filePath);
                        throw new CustomException(e.getMessage(), HttpStatus.BAD_REQUEST);
                    }
                } else {
                    DeleteFolderFiles(filePath);
                    throw new CustomException("파일 생성 시간이 유효하지 않습니다.", HttpStatus.BAD_REQUEST);
                }

                // 마지막에 임시 파일 삭제
                DeleteFolderFiles(filePath);
                return new ResponseEntity<>(response, HttpStatus.OK);

            } catch (Exception e) {
                System.out.print("Error: " + e.getMessage());
                throw new CustomException("파일 업로드 오류 발생", HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }else{
            throw new CustomException("설치된 앱 목록이 존재하지 않습니다.", HttpStatus.NOT_FOUND);
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
                            throw new CustomException("파일 삭제 중 오류 발생", HttpStatus.BAD_REQUEST);
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

        try (ImageAnnotatorClient client = ImageAnnotatorClient.create()) {
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
            System.out.println("OCR 처리 중 예외 발생: " + (e.getMessage() != null ? e.getMessage() : "메시지 없음"));
            throw new CustomException("OCR 처리 중 오류가 발생했습니다.", HttpStatus.BAD_REQUEST);
        }
    }

    public enum phoneType {
        NONE, IOS, ANDROID,
    }

    private Map<String, Object> filterExtractedTexts(List<String> extractedTexts, List<String> savedInstalledApps){
        Map<String, Object> result = new HashMap<>();
        List<Map<String, String>> categories = new ArrayList<>();

        //Pattern datePattern = Pattern.compile("(\\d+)월 (\\d+)일");
        Pattern timePattern = Pattern.compile("(\\d+)시간 (\\d+)분");
        Pattern minutesPattern = Pattern.compile("(\\d+)분");

        boolean mainTimeCaptured = false;
        AtomicInteger timeCount = new AtomicInteger(0);
        boolean isCategory = false;

        for(String text : extractedTexts){
            // 날짜 추출
            //Matcher dateMatcher = datePattern.matcher(text);
            Matcher mainTimeMatcher = timePattern.matcher(text);

            if(!result.containsKey("date")){
                result.put("date", LocalDate.now().toString());
                continue;
            }
            // 메인 시간 추출
            if(!mainTimeCaptured && mainTimeMatcher.matches()){
                String mainTime = TimeFormatter(mainTimeMatcher);
                result.put("mainTime", mainTime);

                mainTimeCaptured = true;
                timeCount.incrementAndGet();

                continue;
            }

            if(text.equals("최다 사용") || text.equals("카테고리 보기") || text.equals("많이 사용한 앱")) {
                isCategory = true;
                continue;
            }

            if(mainTimeCaptured && isCategory){
                getCategoriesInfo(timeCount, timePattern, minutesPattern, text, categories, savedInstalledApps);
            }
        }

        result.put("categories", categories);
        return result;
    }

    private void getCategoriesInfo(AtomicInteger timeCount, Pattern timePattern, Pattern minutesPattern, String text, List<Map<String, String>> categories, List<String> savedInstalledApps){
        try{
            if(timeCount.get() <= 3){
                Matcher MatcherFullTime = timePattern.matcher(text);
                Matcher MinMatcher = minutesPattern.matcher(text);

                boolean fullTimeMatch = MatcherFullTime.matches();
                boolean minMatch = MinMatcher.matches();
                if(fullTimeMatch || minMatch){
                    String subTime = fullTimeMatch ? TimeFormatter(MatcherFullTime) : TimeFormatter(MinMatcher);
                    Map<String, String> categoryTime = categories.get(timeCount.get() - 1);
                    categoryTime.put("time", subTime);

                    timeCount.incrementAndGet();
                }else{
                    if (text.length() > 1 && text.matches(".*[가-힣a-zA-Z0-9].*")) {
                        if(savedInstalledApps.contains(text)){
                            Map<String, String> category = new HashMap<>();
                            category.put("name", text);
                            categories.add(category);
                        }
                    }
                }
            }
        }catch(Exception error){
            System.out.println("Error: " + error.getMessage());
            throw new CustomException("그래프 분석 중 오류가 발생하였습니다.", HttpStatus.BAD_REQUEST);
        }

    }

    private boolean IsInValidScreenShot(String user_id, LocalDateTime fileCreationDateTime) throws ExecutionException, InterruptedException {
        String fileCreationDate = fileCreationDateTime.toLocalDate().toString();
        String fileCreationTime = fileCreationDateTime.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm:ss"));

        var screenTimeData = setScreenTimeRepository.findByDate(user_id, LocalDate.now().toString());
        System.out.println(LocalDate.now());
        if(screenTimeData == null){
            throw new CustomException("스크린 타임 시간이 설정되지 않았습니다!", HttpStatus.BAD_REQUEST);
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

    private String DateFormatter(Matcher dateMatcher){
        int year = LocalDate.now().getYear();
        int month = Integer.parseInt(dateMatcher.group(1));
        int date = Integer.parseInt(dateMatcher.group(2));

        LocalDate parsedDate = LocalDate.of(year, month, date);

        // yyyy-MM-dd 형식으로 포맷
        return parsedDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }

    private String TimeFormatter(Matcher matcher) {
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
