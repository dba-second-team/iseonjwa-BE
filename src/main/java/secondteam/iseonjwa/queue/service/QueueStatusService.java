package secondteam.iseonjwa.queue.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import secondteam.iseonjwa.global.error.BusinessException;
import secondteam.iseonjwa.global.error.ErrorCode;
import secondteam.iseonjwa.member.repository.MemberRepository;
import secondteam.iseonjwa.queue.dto.QueueStatus;
import secondteam.iseonjwa.queue.dto.QueueStatusResponse;
import secondteam.iseonjwa.queue.repository.QueueEntrySnapshot;
import secondteam.iseonjwa.queue.repository.QueueRepository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

@Service
public class QueueStatusService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final MemberRepository memberRepository;
    private final QueueRepository queueRepository;
    private final long maxActiveSize;

    public QueueStatusService(
            MemberRepository memberRepository,
            QueueRepository queueRepository,
            @Value("${queue.active.max-size:4}") long maxActiveSize) {
        if (maxActiveSize < 1) {
            throw new IllegalArgumentException("queue.active.max-size는 1 이상이어야 합니다.");
        }
        this.memberRepository = memberRepository;
        this.queueRepository = queueRepository;
        this.maxActiveSize = maxActiveSize;
    }

    /**
     * 회원의 현재 큐 상태를 통합 조회한다. (4.2 큐 상태 통합 조회)
     * <p>
     * 대기열({@code WAITING})에 있으면 대기 순번과 작업열 남은 자리 수 기반 입장 가능 여부({@code canEnter})를 반환하고,
     * 작업열({@code ACTIVE})에 있으면 만료 시각({@code expiresAt})을 반환한다.
     * 어디에도 없거나 이미 만료된 경우 {@code QUEUE_ENTRY_NOT_FOUND(404)}를 발생시킨다.
     */
    public QueueStatusResponse getStatus(Long memberId) {
        if (memberId == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (!memberRepository.existsById(memberId)) {
            throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND);
        }

        QueueEntrySnapshot snapshot = queueRepository.findMemberQueueState(memberId, Instant.now())
                .orElseThrow(() -> new BusinessException(ErrorCode.QUEUE_ENTRY_NOT_FOUND));

        if (snapshot.status() == QueueStatus.WAITING) {
            long availableSlots = Math.max(0L, maxActiveSize - snapshot.validActiveCount());
            boolean canEnter = snapshot.position() <= availableSlots;
            return new QueueStatusResponse(
                    snapshot.performanceId(),
                    QueueStatus.WAITING,
                    snapshot.position(),
                    canEnter,
                    null
            );
        }

        OffsetDateTime expiresAt = OffsetDateTime.ofInstant(snapshot.expiresAt(), ZONE)
                .truncatedTo(ChronoUnit.SECONDS);
        return new QueueStatusResponse(
                snapshot.performanceId(),
                QueueStatus.ACTIVE,
                0L,
                true,
                expiresAt
        );
    }
}
