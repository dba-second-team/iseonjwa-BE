package secondteam.iseonjwa.reservation.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import secondteam.iseonjwa.global.common.ApiResponse;
import secondteam.iseonjwa.reservation.dto.CreateReservationRequest;
import secondteam.iseonjwa.reservation.dto.CreateReservationResponse;
import secondteam.iseonjwa.reservation.dto.MyReservationResponse;
import secondteam.iseonjwa.reservation.service.ReservationConfirmService;
import secondteam.iseonjwa.reservation.service.ReservationQueryService;

@Tag(name = "예매")
@RestController
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationConfirmService reservationConfirmService;
    private final ReservationQueryService reservationQueryService;

    @PostMapping("/api/reservations")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CreateReservationResponse> createReservation(
            @RequestHeader("X-Member-Id") Long memberId,
            @RequestBody @Valid CreateReservationRequest request) {
        return ApiResponse.ok(reservationConfirmService.confirm(memberId, request));
    }

    @GetMapping("/api/members/me/reservations")
    public ApiResponse<MyReservationResponse> getMyReservations(
            @RequestHeader("X-Member-Id") Long memberId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(reservationQueryService.getMyReservations(memberId, page, size));
    }
}
