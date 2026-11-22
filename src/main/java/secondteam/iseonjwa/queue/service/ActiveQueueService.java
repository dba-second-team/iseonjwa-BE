
    package secondteam.iseonjwa.queue.service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import secondteam.iseonjwa.global.error.BusinessException;
import secondteam.iseonjwa.global.error.ErrorCode;
import secondteam.iseonjwa.member.repository.MemberRepository;
import secondteam.iseonjwa.performance.repository.PerformanceRepository;
import secondteam.iseonjwa.queue.dto.ActiveQueueEnterRequest;
import secondteam.iseonjwa.queue.dto.ActiveQueueEnterResponse;
import secondteam.iseonjwa.queue.dto.QueueStatus;
import secondteam.iseonjwa.queue.repository.ActiveQueueAdmission;
import secondteam.iseonjwa.queue.repository.QueueRepository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

@Service
public class ActiveQueueService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final MemberRepository memberRepository;
    private final PerformanceRepository performanceRepository;
    private final QueueRepository queueRepository;
    private final long maxActiveSize;
    private final long activeTtlSeconds;

    public ActiveQueueService(
            MemberRepository memberRepository,
            PerformanceRepository performanceRepository,
            QueueRepository queueRepository,
            @Value("${queue.active.max-size:4}") long maxActiveSize,
            @Value("${queue.active.ttl-seconds:600}") long activeTtlSeconds) {
        if (maxActiveSize < 1) {
            throw new IllegalArgumentException("queue.active.max-size는 1 이상이어야 합니다.");
        }
        if (activeTtlSeconds < 1) {
            throw new IllegalArgumentException("queue.active.ttl-seconds는 1 이상이어야 합니다.");
        }
        this.memberRepository = memberRepository;
        this.performanceRepository = performanceRepository;
        this.queueRepository = queueRepository;
        this.maxActiveSize = maxActiveSize;
        this.activeTtlSeconds = activeTtlSeconds;
    }

    /**
     * 입장 순서가 된 회원을 대기열에서 제거하고 예매 작업열(active_queue)에 등록한다. (4.3 작업열 진입)
     * <p>
     * 이미 같은 공연의 유효한 작업열에 들어가 있는 경우 타이머 연장 없이 기존 입장·만료 시각을 그대로 반환한다.
     */
    public ActiveQueueEnterResponse enter(Long memberId, ActiveQueueEnterRequest request) {
        if (memberId == null || request == null || request.performanceId() == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        long performanceId = request.performanceId();
        if (!memberRepository.existsById(memberId)) {
            throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND);
        }
        if (!performanceRepository.existsById(performanceId)) {
            throw new BusinessException(ErrorCode.PERFORMANCE_NOT_FOUND);
        }

        Instant now = Instant.now();
        Instant targetExpiresAt = now.plusSeconds(activeTtlSeconds);
        ActiveQueueAdmission admission = queueRepository.enterActiveQueue(
                memberId,
                performanceId,
                now,
                targetExpiresAt,
                maxActiveSize
        );

        OffsetDateTime admittedAt = OffsetDateTime.ofInstant(admission.admittedAt(), ZONE)
                .truncatedTo(ChronoUnit.SECONDS);
        OffsetDateTime expiresAt = OffsetDateTime.ofInstant(admission.expiresAt(), ZONE)
                .truncatedTo(ChronoUnit.SECONDS);

        return new ActiveQueueEnterResponse(
                performanceId,
                QueueStatus.ACTIVE,
                admittedAt,
                expiresAt
        );
    }
}
