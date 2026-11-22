package secondteam.iseonjwa.performance.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import secondteam.iseonjwa.global.common.ApiResponse;
import secondteam.iseonjwa.performance.dto.PerformanceSummaryResponse;
import secondteam.iseonjwa.performance.service.PerformanceQueryService;

@Tag(name = "공연")
@RestController
@RequestMapping("/api/performances")
@RequiredArgsConstructor
public class PerformanceController {
    private final PerformanceQueryService performanceQueryService;

    @Operation(summary = "공연 전체 목록 조회")
    @GetMapping
    public ApiResponse<PerformanceSummaryResponse> list(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        return ApiResponse.ok(performanceQueryService.list(page, size));
    }
}