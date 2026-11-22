package secondteam.iseonjwa.queue.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import secondteam.iseonjwa.global.error.BusinessException;
import secondteam.iseonjwa.global.error.ErrorCode;
import secondteam.iseonjwa.member.repository.MemberRepository;
import secondteam.iseonjwa.queue.repository.QueueRepository;

@Service
@RequiredArgsConstructor
public class QueueLeaveService {

    private final MemberRepository memberRepository;
    private final QueueRepository queueRepository;

    /**
     * 회원의 큐 자진 퇴장 요청을 처리한다. (4.4 큐 자진 퇴장)
     * <p>
     * 대기열(waiting_queue) 또는 예매 작업열(active_queue)에 있으면 즉시 제거하며,
     * 이미 큐에 없거나 만료된 상태여도 멱등하게 성공 처리한다.
     */
    public void leave(Long memberId) {
        if (memberId == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (!memberRepository.existsById(memberId)) {
            throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND);
        }

        queueRepository.leaveQueue(memberId);
    }
}
