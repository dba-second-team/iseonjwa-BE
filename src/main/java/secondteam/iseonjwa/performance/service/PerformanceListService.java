package secondteam.iseonjwa.performance.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import secondteam.iseonjwa.admin.dto.PerformanceListResponse;
import secondteam.iseonjwa.global.error.BusinessException;
import secondteam.iseonjwa.global.error.ErrorCode;
import secondteam.iseonjwa.performance.entity.Performance;
import secondteam.iseonjwa.performance.repository.PerformanceListRepository;
import secondteam.iseonjwa.reservation.entity.PaymentStatus;
import secondteam.iseonjwa.reservation.entity.ReservationStatus;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PerformanceListService {
    private static final int MAX_PAGE_SIZE = 100;

    private final PerformanceListRepository performanceListRepository;

    public PerformanceListResponse list(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE || (long) page * size > Integer.MAX_VALUE) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        var performances = performanceListRepository.findAllBy(
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "performanceId")));
        if (performances.isEmpty()) {
            return new PerformanceListResponse(List.of(), page, size, performances.getTotalElements());
        }

        List<Long> performanceIds = performances.stream().map(Performance::getPerformanceId).toList();
        var ticketCounts = performanceListRepository
                .countSoldTickets(performanceIds, ReservationStatus.CONFIRMED, PaymentStatus.PAID)
                .stream()
                .collect(Collectors.toMap(PerformanceListRepository.TicketCount::getPerformanceId,
                        PerformanceListRepository.TicketCount::getSoldTicketCount));
        var items = performances.stream()
                .map(performance -> new PerformanceListResponse.Item(
                        performance.getPerformanceId(),
                        performance.getTitle(),
                        performance.getGenre(),
                        null,
                        ticketCounts.getOrDefault(performance.getPerformanceId(), 0L)))
                .toList();

        return new PerformanceListResponse(items, page, size, performances.getTotalElements());
    }
}
