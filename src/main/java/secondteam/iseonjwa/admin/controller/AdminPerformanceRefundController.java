package secondteam.iseonjwa.admin.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import secondteam.iseonjwa.admin.dto.AdminRefundRequest;
import secondteam.iseonjwa.admin.dto.AdminRefundResponse;
import secondteam.iseonjwa.global.common.ApiResponse;
import secondteam.iseonjwa.global.error.ErrorCode;
import secondteam.iseonjwa.global.error.ErrorResponse;
import secondteam.iseonjwa.reservation.service.AdminRefundService;

@Tag(name = "관리자")
@RestController
@RequestMapping("/api/admin/performances/{performanceId}/refunds")
@RequiredArgsConstructor
public class AdminPerformanceRefundController {
    private final AdminRefundService refundService;

    @Operation(summary = "선택한 예매의 환불 완료 기록 및 좌석 반환")
    @PostMapping
    public ApiResponse<AdminRefundResponse> complete(
            @PathVariable("performanceId") long performanceId,
            @Valid @RequestBody AdminRefundRequest request
    ) {
        return ApiResponse.ok(refundService.complete(performanceId, request.reservationIds()));
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ErrorResponse> handleInvalidRequest() {
        return ResponseEntity.badRequest().body(ErrorResponse.of(ErrorCode.INVALID_REQUEST));
    }
}
