package com.example.iplan.Controller;

import com.example.iplan.Domain.DayData;
import com.example.iplan.Service.CalendarService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.checkerframework.checker.units.qual.A;
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
@RequestMapping("/calendar")
@Tag(name = "달력 페이지 컨트롤러", description = "선택한 한달 간의 데이터를 처리, 관리한다.")
public class CalendarController {

    private final CalendarService calendarService;

    /**
     * (달력 탭) 해당 년월의 도장(성공)상황 모두 가져오기
     * @param yearMonth
     * @param userId
     * @return
     * @throws ExecutionException
     * @throws InterruptedException
     */
    @Operation(summary = "한 달간 보상 지급 완료 (success = true) 된 데이터 리스트 GET", description = "해당 년/월에 해당하는 하루 데이터에서 보상 지급 완료된 월/일, 도장 여부 데이터를 가져온다.",
            parameters = {
                    @Parameter(name = "yearMonth", description = "원하는 년/월", example = "2025-01", required = true)
            })
    @GetMapping("/{yearMonth}")
    public ResponseEntity<Map<String, Object>> getMonthCalendarData
    (@PathVariable @Parameter(description = "원하는 년/월", example = "2025-01") String yearMonth, @AuthenticationPrincipal String userId) {

        return calendarService.getAllCalendarData(yearMonth, userId);

    }

    /**
     * (달력 탭) 특정 날짜 클릭시 해당 날짜의 데이터 모두 가져오기
     * @param yearMonthDate
     * @param userId
     * @return
     * @throws ExecutionException
     * @throws InterruptedException
     */
    @Operation(summary = "해당 날짜의 계획 진행 데이터 모두 가져오기", description = "달력에서 특정 날짜 클릭시 해당 날의 계획 진행 정도를 보여 준다.",
            parameters = {
                    @Parameter(name = "yearMonthDate", description = "원하는 년/월/일", example = "2025-01-22", required = true)
            })
    @ApiResponses(value = {
            @ApiResponse(content = @Content(schema = @Schema(implementation = DayData.class))),
    })
    @GetMapping("/showTargetDateData/{yearMonthDate}")
    public ResponseEntity<Map<String, Object>> getTargetDateData
    (@PathVariable @Parameter(description = "원하는 년/월/일", example = "2025-01-22") String yearMonthDate, @AuthenticationPrincipal String userId) throws ExecutionException, InterruptedException {
        return calendarService.getTargetDateData(yearMonthDate, userId);
    }
}
