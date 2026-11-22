package secondteam.iseonjwa.performance.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import secondteam.iseonjwa.global.error.BusinessException;
import secondteam.iseonjwa.global.error.ErrorCode;
import secondteam.iseonjwa.performance.dto.PerformanceSummaryResponse;
import secondteam.iseonjwa.performance.entity.Performance;
import secondteam.iseonjwa.performance.entity.PerformanceStatus;
import secondteam.iseonjwa.performance.repository.PerformanceQueryRepository;
import secondteam.iseonjwa.performance.repository.PerformanceQueryRepository.PriceRange;
import secondteam.iseonjwa.performance.repository.PerformanceQueryRepository.SchedulePeriod;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PerformanceQueryService {
    private static final int MAX_PAGE_SIZE = 100;

    private final PerformanceQueryRepository performanceQueryRepository;

    public PerformanceSummaryResponse list(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE || (long) page * size > Integer.MAX_VALUE) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        // 취소된 공연은 회원에게 보여 주지 않는다
        var performances = performanceQueryRepository.findAllByStatusNot(
                PerformanceStatus.CANCELED,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "performanceId")));
        if (performances.isEmpty()) {
            return new PerformanceSummaryResponse(List.of(), page, size, performances.getTotalElements());
        }

        // 공연마다 쿼리를 날리지 않도록 현재 페이지의 공연 ID로 한 번에 집계한다
        List<Long> performanceIds = performances.stream().map(Performance::getPerformanceId).toList();
        Map<Long, SchedulePeriod> periods = performanceQueryRepository.findSchedulePeriods(performanceIds).stream()
                .collect(Collectors.toMap(SchedulePeriod::getPerformanceId, Function.identity()));
        Map<Long, PriceRange> priceRanges = performanceQueryRepository.findPriceRanges(performanceIds).stream()
                .collect(Collectors.toMap(PriceRange::getPerformanceId, Function.identity()));

        var items = performances.stream()
                .map(performance -> toItem(performance,
                        periods.get(performance.getPerformanceId()),
                        priceRanges.get(performance.getPerformanceId())))
                .toList();

        return new PerformanceSummaryResponse(items, page, size, performances.getTotalElements());
    }

    private PerformanceSummaryResponse.Item toItem(Performance performance, SchedulePeriod period, PriceRange priceRange) {
        return new PerformanceSummaryResponse.Item(
                performance.getPerformanceId(),
                performance.getTitle(),
                performance.getGenre(),
                null, // TODO: 포스터 S3 연동(#36) 머지 후 PosterStorage.readUrl(posterKey) 연결
                performance.getStatus(),
                period == null ? null : period.getFirstStartAt().toLocalDate(),
                period == null ? null : period.getLastStartAt().toLocalDate(),
                priceRange == null ? null : priceRange.getMinPrice(),
                priceRange == null ? null : priceRange.getMaxPrice()
        );
    }
}