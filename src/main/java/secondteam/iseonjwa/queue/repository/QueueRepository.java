package secondteam.iseonjwa.queue.repository;

import java.time.Instant;

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
}
