package secondteam.iseonjwa.admin.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import secondteam.iseonjwa.admin.dto.PerformanceListResponse;
import secondteam.iseonjwa.global.common.ApiResponse;
import secondteam.iseonjwa.global.error.ErrorCode;
import secondteam.iseonjwa.global.error.ErrorResponse;
import secondteam.iseonjwa.performance.service.PerformanceListService;

@Tag(name = "관리자")
@RestController
@RequestMapping("/api/admin/performances")
@RequiredArgsConstructor
public class AdminPerformanceListController {
    private final PerformanceListService performanceListService;

    @GetMapping
    public ApiResponse<PerformanceListResponse> list(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        return ApiResponse.ok(performanceListService.list(page, size));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleInvalidPagination() {
        return ResponseEntity.badRequest().body(ErrorResponse.of(ErrorCode.INVALID_REQUEST));
    }
}
