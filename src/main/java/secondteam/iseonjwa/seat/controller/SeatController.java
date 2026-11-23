package secondteam.iseonjwa.seat.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import secondteam.iseonjwa.global.common.ApiResponse;
import secondteam.iseonjwa.global.error.ErrorCode;
import secondteam.iseonjwa.global.error.ErrorResponse;
import secondteam.iseonjwa.seat.dto.SeatHoldRequest;
import secondteam.iseonjwa.seat.dto.SeatHoldResponse;
import secondteam.iseonjwa.seat.dto.SeatLayoutResponse;
import secondteam.iseonjwa.seat.service.SeatLayoutService;
import secondteam.iseonjwa.seat.service.SeatService;

@Tag(name = "좌석")
@RestController
@RequestMapping("/api/schedules/{scheduleId}/seats")
@RequiredArgsConstructor
public class SeatController {
    private final SeatService seatService;
    private final SeatLayoutService seatLayoutService;

    @Operation(summary = "좌석 배치 조회 (API-15)",
            description = "X-Member-Id를 보내면 내가 선점 중인 좌석에 heldByMe=true를 표시한다. "
                    + "선점 시간이 지난 HELD 좌석은 AVAILABLE로 내려준다.")
    @GetMapping
    public ApiResponse<SeatLayoutResponse> getSeatLayout(
            @PathVariable long scheduleId,
            @RequestHeader(value = "X-Member-Id", required = false) Long memberId) {
        return ApiResponse.ok(seatLayoutService.getLayout(scheduleId, memberId));
    }

    @Operation(summary = "좌석 선점 (API-6)")
    @PostMapping("/hold")
    public ApiResponse<SeatHoldResponse> holdSeats(@PathVariable long scheduleId,
                                                   @RequestHeader("X-Member-Id") long memberId,
                                                   @RequestBody @Valid SeatHoldRequest request) {
        return ApiResponse.ok(seatService.holdSeats(memberId, scheduleId, request.seatIds()));
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    ResponseEntity<ErrorResponse> handleInvalidRequest() {
        return ResponseEntity.badRequest().body(ErrorResponse.of(ErrorCode.INVALID_REQUEST));
    }
}
