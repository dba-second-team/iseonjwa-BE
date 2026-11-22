package secondteam.iseonjwa.reservation.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import secondteam.iseonjwa.admin.dto.PerformanceReservationListResponse;
import secondteam.iseonjwa.admin.dto.PerformanceReservationListResponse.Item;
import secondteam.iseonjwa.admin.dto.PerformanceReservationListResponse.SeatItem;
import secondteam.iseonjwa.global.error.BusinessException;
import secondteam.iseonjwa.global.error.ErrorCode;
import secondteam.iseonjwa.reservation.entity.PaymentStatus;
import secondteam.iseonjwa.reservation.repository.AdminReservationQueryRepository;
import secondteam.iseonjwa.reservation.repository.AdminReservationQueryRepository.ReservationRow;
import secondteam.iseonjwa.reservation.repository.AdminReservationQueryRepository.SeatRow;

import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminReservationQueryService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final AdminReservationQueryRepository repository;

    public PerformanceReservationListResponse list(long performanceId, PaymentStatus status, int page, int size) {
        if (performanceId < 1 || page < 0 || size < 1 || size > MAX_PAGE_SIZE
                || (long) page * size > Integer.MAX_VALUE) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        var reservations = repository.findReservationPage(performanceId, status, PageRequest.of(page, size));
        long totalCount = reservations.getTotalElements();
        if (reservations.isEmpty()) {
            if (totalCount == 0 && !repository.existsPerformance(performanceId)) {
                throw new BusinessException(ErrorCode.PERFORMANCE_NOT_FOUND);
            }
            return new PerformanceReservationListResponse(List.of(), page, size, totalCount);
        }

        List<Long> reservationIds = reservations.stream().map(ReservationRow::getReservationId).toList();
        var seatsByReservation = repository.findSeats(reservationIds).stream()
                .collect(Collectors.groupingBy(SeatRow::getReservationId, Collectors.mapping(
                        seat -> new SeatItem(seat.getSection(), seat.getRowNum(), seat.getSeatNum()),
                        Collectors.toList())));

        List<Item> items = reservations.stream()
                .map(row -> toItem(row, seatsByReservation.getOrDefault(row.getReservationId(), List.of()), status))
                .toList();
        return new PerformanceReservationListResponse(items, page, size, totalCount);
    }

    private Item toItem(ReservationRow row, List<SeatItem> seats, PaymentStatus status) {
        Integer expectedRefundAmount = status == PaymentStatus.CANCELED
                ? row.getPaidAmount() - row.getCancelFee()
                : null;
        Integer refundAmount = status == PaymentStatus.REFUNDED ? row.getRefundAmount() : null;
        return new Item(
                row.getReservationId(),
                row.getMemberName(),
                row.getScheduleId(),
                row.getScheduleNumber(),
                row.getStartAt().atZone(SEOUL).toOffsetDateTime(),
                row.getReservedAt().atZone(SEOUL).toOffsetDateTime(),
                seats,
                seats.size(),
                row.getPaidAmount(),
                status,
                expectedRefundAmount,
                refundAmount);
    }
}
