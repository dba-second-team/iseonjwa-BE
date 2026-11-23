package secondteam.iseonjwa.seat.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import secondteam.iseonjwa.global.error.BusinessException;
import secondteam.iseonjwa.global.error.ErrorCode;
import secondteam.iseonjwa.performance.entity.Price;
import secondteam.iseonjwa.performance.entity.Schedule;
import secondteam.iseonjwa.performance.repository.PriceRepository;
import secondteam.iseonjwa.performance.repository.ScheduleRepository;
import secondteam.iseonjwa.seat.dto.SeatLayoutItem;
import secondteam.iseonjwa.seat.dto.SeatLayoutResponse;
import secondteam.iseonjwa.seat.entity.SeatSection;
import secondteam.iseonjwa.seat.entity.SeatStatus;
import secondteam.iseonjwa.seat.repository.SeatLayoutRow;
import secondteam.iseonjwa.seat.repository.SeatRepository;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeatLayoutService {
    private static final ZoneId SEAT_TIME_ZONE = ZoneId.of("Asia/Seoul");

    private static final Comparator<SeatLayoutItem> LAYOUT_ORDER = Comparator
            .comparing(SeatLayoutItem::section)
            .thenComparing(SeatLayoutItem::rowNum, SeatLayoutService::compareRowNum)
            .thenComparing(SeatLayoutItem::seatNum);

    private final ScheduleRepository scheduleRepository;
    private final SeatRepository seatRepository;
    private final PriceRepository priceRepository;

    public SeatLayoutResponse getLayout(long scheduleId, Long memberId) {
        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCHEDULE_NOT_FOUND));
        Map<SeatSection, Integer> prices = findPrices(schedule.getPerformance().getPerformanceId());
        LocalDateTime now = LocalDateTime.now(SEAT_TIME_ZONE);

        List<SeatLayoutItem> seats = seatRepository.findLayoutByScheduleId(scheduleId).stream()
                .map(row -> toItem(row, prices.get(row.section()), memberId, now))
                .sorted(LAYOUT_ORDER)
                .toList();
        return new SeatLayoutResponse(scheduleId, seats);
    }

    private Map<SeatSection, Integer> findPrices(Long performanceId) {
        Map<SeatSection, Integer> prices = new EnumMap<>(SeatSection.class);
        for (Price price : priceRepository.findByPerformanceId(performanceId)) {
            prices.put(price.getSection(), price.getPrice());
        }
        return prices;
    }

    private static SeatLayoutItem toItem(SeatLayoutRow row, Integer price, Long memberId, LocalDateTime now) {
        boolean validHold = row.status() == SeatStatus.HELD
                && row.heldUntil() != null
                && row.heldUntil().isAfter(now);
        // 선점 시간이 지난 좌석은 만료 처리 전이라도 고를 수 있게 AVAILABLE로 내려준다
        SeatStatus status = row.status() == SeatStatus.HELD && !validHold ? SeatStatus.AVAILABLE : row.status();
        boolean heldByMe = validHold && memberId != null && memberId.equals(row.heldByMemberId());
        return new SeatLayoutItem(row.seatId(), row.section(), row.rowNum(), row.seatNum(), status, price, heldByMe);
    }

    private static int compareRowNum(String left, String right) {
        boolean leftNumeric = isNumeric(left);
        boolean rightNumeric = isNumeric(right);
        if (leftNumeric && rightNumeric) {
            return Integer.compare(Integer.parseInt(left), Integer.parseInt(right));
        }
        if (leftNumeric != rightNumeric) {
            return leftNumeric ? -1 : 1;
        }
        return left.compareTo(right);
    }

    private static boolean isNumeric(String value) {
        return !value.isEmpty() && value.length() <= 9 && value.chars().allMatch(Character::isDigit);
    }
}
