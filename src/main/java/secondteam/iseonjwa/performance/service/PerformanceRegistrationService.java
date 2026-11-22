package secondteam.iseonjwa.performance.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import secondteam.iseonjwa.admin.dto.PerformanceCreateRequest;
import secondteam.iseonjwa.admin.dto.PerformanceCreateResponse;
import secondteam.iseonjwa.global.error.BusinessException;
import secondteam.iseonjwa.global.error.ErrorCode;
import secondteam.iseonjwa.member.repository.MemberRepository;
import secondteam.iseonjwa.performance.entity.Performance;
import secondteam.iseonjwa.performance.entity.PerformanceStatus;
import secondteam.iseonjwa.performance.entity.Price;
import secondteam.iseonjwa.performance.entity.Schedule;
import secondteam.iseonjwa.performance.repository.PerformanceRepository;
import secondteam.iseonjwa.performance.repository.PriceRepository;
import secondteam.iseonjwa.performance.repository.ScheduleRepository;
import secondteam.iseonjwa.seat.entity.SeatSection;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PerformanceRegistrationService {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final MemberRepository memberRepository;
    private final PerformanceRepository performanceRepository;
    private final ScheduleRepository scheduleRepository;
    private final PriceRepository priceRepository;
    private final PlatformTransactionManager transactionManager;
    private final PosterStorage posterStorage;

    public PerformanceCreateResponse register(
            long adminId, PerformanceCreateRequest request, MultipartFile poster
    ) {
        Map<SeatSection, Integer> prices = sectionPrices(request);
        List<LocalDateTime> startTimes = request.schedules().stream()
                .map(schedule -> schedule.startAt().atZoneSameInstant(SEOUL).toLocalDateTime())
                .sorted()
                .toList();
        String venueName = request.venueName().strip();
        String posterKey = posterStorage.upload(poster);

        try {
            return new TransactionTemplate(transactionManager).execute(status -> {
                if (posterKey != null) {
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override
                        public void afterCompletion(int completionStatus) {
                            if (completionStatus == STATUS_ROLLED_BACK) {
                                posterStorage.deleteAfterFailure(posterKey);
                            }
                        }
                    });
                }
                return saveRegistration(adminId, request, prices, startTimes, venueName, posterKey);
            });
        } catch (CannotCreateTransactionException e) {
            posterStorage.deleteAfterFailure(posterKey);
            throw e;
        }
    }

    private PerformanceCreateResponse saveRegistration(
            long adminId, PerformanceCreateRequest request, Map<SeatSection, Integer> prices,
            List<LocalDateTime> startTimes, String venueName, String posterKey
    ) {
        // There is one administrator. Serialize registrations before checking venue availability.
        memberRepository.findByMemberId(adminId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        validateScheduleAvailability(venueName, startTimes, request.runningTime());

        Performance performance = performanceRepository.save(Performance.builder()
                .title(request.title().strip())
                .genre(request.genre())
                .venueName(venueName)
                .description(request.description())
                .posterKey(posterKey)
                .runningTime(request.runningTime())
                .maxTicketsPerMember(request.maxTicketsPerMember())
                .status(PerformanceStatus.UPCOMING)
                .build());

        List<Schedule> schedules = scheduleRepository.saveAll(startTimes.stream()
                .map(startAt -> Schedule.builder()
                        .startAt(startAt)
                        .performance(performance)
                        .build())
                .toList());
        priceRepository.saveAll(prices.entrySet().stream()
                .map(entry -> Price.builder()
                        .section(entry.getKey())
                        .price(entry.getValue())
                        .performance(performance)
                        .build())
                .toList());

        return new PerformanceCreateResponse(
                performance.getPerformanceId(),
                performance.getStatus(),
                schedules.stream()
                        .map(schedule -> new PerformanceCreateResponse.ScheduleResponse(
                                schedule.getScheduleId(), schedule.getStartAt().atZone(SEOUL).toOffsetDateTime()))
                        .toList());
    }

    private Map<SeatSection, Integer> sectionPrices(PerformanceCreateRequest request) {
        Map<SeatSection, Integer> prices = new EnumMap<>(SeatSection.class);
        for (PerformanceCreateRequest.SectionPriceRequest price : request.prices()) {
            if (prices.putIfAbsent(price.section(), price.price()) != null) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST);
            }
        }
        return prices;
    }

    private void validateScheduleAvailability(String venueName, List<LocalDateTime> startTimes, int runningTime) {
        for (int index = 1; index < startTimes.size(); index++) {
            LocalDateTime previousEnd = startTimes.get(index - 1).plusMinutes(runningTime);
            if (previousEnd.isAfter(startTimes.get(index))) {
                throw new BusinessException(ErrorCode.SCHEDULE_CONFLICT);
            }
        }

        LocalDateTime latestEndAt = startTimes.getLast().plusMinutes(runningTime);
        for (Schedule existing : scheduleRepository.findVenueSchedulesBefore(venueName, latestEndAt)) {
            LocalDateTime existingStart = existing.getStartAt();
            LocalDateTime existingEnd = existingStart.plusMinutes(existing.getPerformance().getRunningTime());
            boolean overlaps = startTimes.stream().anyMatch(start ->
                    start.isBefore(existingEnd) && existingStart.isBefore(start.plusMinutes(runningTime)));
            if (overlaps) {
                throw new BusinessException(ErrorCode.SCHEDULE_CONFLICT);
            }
        }
    }
}
