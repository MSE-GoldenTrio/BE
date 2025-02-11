package com.example.iplan.Service;

import com.example.iplan.Domain.ScreenTimeOCRResult;
import com.example.iplan.ExceptionHandler.CustomException;
import com.example.iplan.Repository.GetScreenTimeOCRRepository;
import com.example.iplan.Repository.SetScreenTimeRepository;
import com.google.cloud.vision.v1.*;
import com.google.protobuf.ByteString;
import com.google.type.DateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScreenTimeService {

    private final SetScreenTimeRepository setScreenTimeRepository;

    private final GetScreenTimeOCRRepository getScreenTimeOCRRepository;

    public ResponseEntity<Map<String, Object>> uploadScreenTimeImage(@RequestParam("file") MultipartFile image, String user_id) throws IOException, ExecutionException, InterruptedException {
        Map<String, Object> response = new HashMap<>();

        // 1. 임시 파일로 저장
        Path tempDir = Files.createTempDirectory("upload");
        Path filePath = tempDir.resolve(Objects.requireNonNull(image.getOriginalFilename()));
        Files.write(filePath, image.getBytes());

        // 2. 파일 생성 시간 확인(캡쳐 시간 확인)
        BasicFileAttributes attr = Files.readAttributes(filePath, BasicFileAttributes.class);
        // FileTime 객체로 생성 시간 가져오기
        FileTime creationTime = attr.creationTime();

        // FileTime을 LocalDateTime으로 변환
        LocalDateTime creationDateTime = creationTime
                .toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();

        System.out.println("파일 생성 시간: "+ creationDateTime);

        // 파일 생성 날짜가 오늘이고, 마감 시간을 넘겼을 경우에만 OCR 수행
        if(IsInValidScreenShot(user_id, creationDateTime)){
            // 3. Google Vision API를 사용하여 OCR 수행
            List<String> extractedTexts = extractTextFromImage(filePath);

            // 커스텀 필터를 통해 필요한 텍스트만 추출
            Map<String, Object> filteredTexts = filterExtractedTexts(extractedTexts);

            // 해당 날짜에 설정해둔 목표 시간에 달성했을 때, 결과물을 담아서 보낸다.
            // 실패시 파일을 삭제하고, 상황에 맞게 오류 발송
            if(!IsAchieveUsingTime(user_id, filteredTexts, response)){
                LocalDate today = LocalDate.now();
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                String todayToString = today.format(formatter);

                ScreenTimeOCRResult result = ScreenTimeOCRResult.builder()
                                .id(user_id)
                                .date(todayToString)
                                .result(filteredTexts)
                                .isSuccess(true)
                                .build();

                getScreenTimeOCRRepository.save(result);
                Files.delete(filePath);
            }
        }else{
            Files.delete(filePath);
            throw new CustomException("파일 생성 시간이 유효하지 않습니다.", HttpStatus.OK);
        }

        Files.delete(filePath);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    private List<String> extractTextFromImage(Path imagePath) throws IOException{
        ByteString imgBytes = ByteString.readFrom(Files.newInputStream(imagePath));

        Image img = Image.newBuilder().setContent(imgBytes).build();
        Feature feat = Feature.newBuilder().setType(Feature.Type.TEXT_DETECTION).build();
        AnnotateImageRequest request = AnnotateImageRequest.newBuilder().addFeatures(feat).setImage(img).build();

        try(ImageAnnotatorClient client = ImageAnnotatorClient.create()){
            AnnotateImageResponse response = client.batchAnnotateImages(java.util.Collections.singletonList(request)).getResponses(0);
            if(response.hasError()){
                System.out.printf("Error: %s%n", response.getError().getMessage());
                return List.of("사진 OCR중 오류 발생");
            }
            return response.getTextAnnotationsList().stream()
                    .map(EntityAnnotation::getDescription)
                    .collect(Collectors.toList());
        }
    }

    private Map<String, Object> filterExtractedTexts(List<String> extractedTexts){
        Map<String, Object> result = new HashMap<>();
        List<Map<String, String>> categories = new ArrayList<>();

        Pattern datePattern = Pattern.compile("(\\d+)월 (\\d+)일");
        Pattern timePattern = Pattern.compile("(\\d+)시간 (\\d+)분");

        boolean mainTimeCaptured = false;
        int timeCount = 0;

        for(String text : extractedTexts){
            // 날짜 추출
            Matcher dateMatcher = datePattern.matcher(text);
            Matcher mainTimeMatcher = timePattern.matcher(text);

            if(!result.containsKey("date") && dateMatcher.find()){
                String date = DateFormatter(dateMatcher);
                result.put("date", date);
            }
            // 메인 시간 추출
            else if(!mainTimeCaptured && mainTimeMatcher.matches()){
                String mainTime = TimeFormatter(mainTimeMatcher);
                result.put("mainTime", mainTime);

                mainTimeCaptured = true;
                timeCount++;
            }

            //카테고리 및 각 카테고리별 시간 추출
            else if(mainTimeCaptured && timeCount > 0){
                Matcher subtimeMatcher = timePattern.matcher(text);
                if(subtimeMatcher.matches()){
                    String subtime = TimeFormatter(subtimeMatcher);
                    Map<String, String> categoryTime = categories.get(categories.size() - 1);
                    categoryTime.put("time", subtime);

                    timeCount++;
                }else{
                    Map<String, String> category = new HashMap<>();
                    category.put("name", text);
                    categories.add(category);
                }
            }
            if(timeCount >= 4) break;
        }

        result.put("categories", categories);
        return result;
    }

    private boolean IsInValidScreenShot(String user_id, LocalDateTime fileCreationDateTime) throws ExecutionException, InterruptedException {
        String fileCreationDate = fileCreationDateTime.toLocalDate().toString();
        String fileCreationTime = fileCreationDateTime.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm:ss"));

        String deadLineTime = setScreenTimeRepository.findByDate(user_id, LocalDate.now().toString()).getDeadLineTime();
        System.out.println("사진 올리는 마감 시간 : " + deadLineTime);

        return fileCreationDate.equals(LocalDate.now().toString()) && LocalTime.parse(fileCreationTime).isAfter(LocalTime.parse(deadLineTime));
    }

    private boolean IsAchieveUsingTime(String user_id, Map<String, Object> filteredTexts, Map<String, Object> response) throws ExecutionException, InterruptedException {
        LocalTime mainTime = LocalTime.parse(filteredTexts.get("mainTime").toString(), DateTimeFormatter.ofPattern("HH:mm"));
        Duration mainTimeDuration = Duration.between(LocalTime.MIDNIGHT, mainTime);

        String goalTimeString = setScreenTimeRepository.findByDate(user_id, LocalDate.now().toString()).getGoalTime();
        LocalTime goalTime = LocalTime.parse(goalTimeString, DateTimeFormatter.ofPattern("HH:mm"));
        Duration goalTimeDuration = Duration.between(LocalTime.MIDNIGHT, goalTime);

        if(mainTimeDuration.compareTo(goalTimeDuration) >= 0){
            response.put("entity", filteredTexts);
            response.put("success", true);
            response.put("message", "목표 시간 달성에 성공하였습니다.");
            return true;
        }else{
            throw new CustomException("목표 시간 달성에 실패하였습니다.", HttpStatus.OK);
        }
    }

    private String DateFormatter(Matcher dateMatcher){
        int year = LocalDate.now().getYear();
        int month = Integer.parseInt(dateMatcher.group(1));
        int date = Integer.parseInt(dateMatcher.group(2));

        LocalDate parsedDate = LocalDate.of(year, month, date);

        // yyyy-MM-dd 형식으로 포맷
        return parsedDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }

    private String TimeFormatter(Matcher timeMatcher){
        int hours = Integer.parseInt(timeMatcher.group(1));
        int minutes = Integer.parseInt(timeMatcher.group(2));
        LocalTime parseTime = LocalTime.of(hours, minutes);

        // HH:mm 형식으로 포맷
        return parseTime.format(DateTimeFormatter.ofPattern("HH:mm"));
    }
}
