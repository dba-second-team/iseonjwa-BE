package secondteam.iseonjwa.admin.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import secondteam.iseonjwa.admin.dto.PerformanceReservationListResponse;
import secondteam.iseonjwa.global.common.ApiResponse;
import secondteam.iseonjwa.global.error.ErrorCode;
import secondteam.iseonjwa.global.error.ErrorResponse;
import secondteam.iseonjwa.reservation.entity.PaymentStatus;
import secondteam.iseonjwa.reservation.service.AdminReservationQueryService;

@Tag(name = "관리자")
@RestController
@RequestMapping("/api/admin/performances/{performanceId}/reservations")
@RequiredArgsConstructor
public class AdminPerformanceReservationController {
    private final AdminReservationQueryService reservationQueryService;

    @Operation(summary = "공연별 예매·취소·환불 내역 조회")
    @GetMapping
    public ApiResponse<PerformanceReservationListResponse> list(
            @PathVariable("performanceId") long performanceId,
            @RequestParam(name = "status", defaultValue = "PAID") PaymentStatus status,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        return ApiResponse.ok(reservationQueryService.list(performanceId, status, page, size));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleInvalidParameter() {
        return ResponseEntity.badRequest().body(ErrorResponse.of(ErrorCode.INVALID_REQUEST));
    }
}
