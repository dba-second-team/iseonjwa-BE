package secondteam.iseonjwa.reservation.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import secondteam.iseonjwa.global.common.ApiResponse;
import secondteam.iseonjwa.reservation.dto.CancelReservationResponse;
import secondteam.iseonjwa.reservation.dto.MyReservationResponse;
import secondteam.iseonjwa.reservation.service.ReservationCancelService;
import secondteam.iseonjwa.reservation.service.ReservationQueryService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@Tag(name = "예매")
@RestController
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationQueryService reservationQueryService;
    private final ReservationCancelService reservationCancelService;

    @GetMapping("/api/members/me/reservations")
    public ApiResponse<MyReservationResponse> getMyReservations(
            @RequestHeader("X-Member-Id") Long memberId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(reservationQueryService.getMyReservations(memberId, page, size));
    }

    @PostMapping("/api/reservations/{reservationId}/cancel")
    public ApiResponse<CancelReservationResponse> cancelReservation(
            @RequestHeader("X-Member-Id") Long memberId,
            @PathVariable Long reservationId) {
        return ApiResponse.ok(reservationCancelService.cancel(memberId, reservationId));
    }
}
