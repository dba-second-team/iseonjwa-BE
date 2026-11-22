package secondteam.iseonjwa.queue.repository;

import java.time.Instant;
import java.util.Optional;

public interface QueueRepository {

    /**
     * 전체 공연의 대기열(waiting_queue)과 유효한 예매 작업열(active_queue) 어디에도
     * 해당 회원이 존재하지 않을 때만 지정 공연의 대기열에 등록하고 1부터 시작하는 순번을 반환한다.
     *
     * @param memberId      진입 요청 회원 ID
     * @param performanceId 진입 대상 공연 ID
     * @param now           진입 기준 시각
     * @return 해당 공연 대기열 내 1-based 대기 순번
     */
    long enqueueWaiting(long memberId, long performanceId, Instant now);

    /**
     * 회원의 현재 큐 상태(대기열 순번·해당 공연의 유효 작업열 인원수 또는 작업열 만료 시각)를 원자적으로 조회한다.
     * 대기열·작업열 어디에도 없거나 이미 만료된 경우 잔여 상태를 정리하고 {@link Optional#empty()}를 반환한다.
     *
     * @param memberId 조회 대상 회원 ID
     * @param now      만료 판정 기준 시각
     * @return 현재 큐 상태 스냅샷
     */
    Optional<QueueEntrySnapshot> findMemberQueueState(long memberId, Instant now);

    /**
     * 입장 순서가 된 대기열 회원을 {@code waiting_queue}에서 제거하고 {@code active_queue}로 원자적으로 이동시킨다.
     * 이미 해당 공연의 유효한 {@code active_queue}에 있는 경우 타이머 연장 없이 기존 시각을 그대로 반환한다.
     *
     * @param memberId      진입 요청 회원 ID
     * @param performanceId 진입 대상 공연 ID
     * @param now           진입 기준 시각
     * @param expiresAt     신규 진입 시 부여할 만료 시각
     * @param maxActiveSize 공연별 작업열 최대 정원
     * @return 작업열 진입 시각 및 만료 시각 스냅샷
     */
    ActiveQueueAdmission enterActiveQueue(
            long memberId,
            long performanceId,
            Instant now,
            Instant expiresAt,
            long maxActiveSize
    );
}
