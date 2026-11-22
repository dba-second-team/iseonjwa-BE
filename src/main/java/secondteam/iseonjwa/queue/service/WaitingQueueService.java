package secondteam.iseonjwa.queue.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import secondteam.iseonjwa.global.error.BusinessException;
import secondteam.iseonjwa.global.error.ErrorCode;
import secondteam.iseonjwa.member.repository.MemberRepository;
import secondteam.iseonjwa.performance.repository.PerformanceRepository;
import secondteam.iseonjwa.queue.dto.QueueStatus;
import secondteam.iseonjwa.queue.dto.WaitingQueueRegisterRequest;
import secondteam.iseonjwa.queue.dto.WaitingQueueRegisterResponse;
import secondteam.iseonjwa.queue.repository.QueueRepository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class WaitingQueueService {

    private final MemberRepository memberRepository;
    private final PerformanceRepository performanceRepository;
    private final QueueRepository queueRepository;

    /**
     * 회원의 공연 예매 대기열 진입 요청을 처리한다. (4.1 대기열 등록)
     * <p>
     * 회원 및 공연 존재 여부를 검증한 뒤, 전체 공연의 대기열(waiting_queue)과
     * 예매 작업열(active_queue) 어디에도 없을 때만 해당 공연 대기열에 등록하고 순번을 반환한다.
     */
    public WaitingQueueRegisterResponse register(Long memberId, WaitingQueueRegisterRequest request) {
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
        long position = queueRepository.enqueueWaiting(memberId, performanceId, now);
        OffsetDateTime enteredAt = OffsetDateTime.ofInstant(now, ZoneId.systemDefault())
                .truncatedTo(ChronoUnit.SECONDS);

        return new WaitingQueueRegisterResponse(
                performanceId,
                QueueStatus.WAITING,
                position,
                enteredAt
        );
    }
}
