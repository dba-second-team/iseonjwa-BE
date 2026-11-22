package secondteam.iseonjwa.queue.controller;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import secondteam.iseonjwa.member.entity.Member;
import secondteam.iseonjwa.member.repository.MemberRepository;
import secondteam.iseonjwa.performance.entity.Genre;
import secondteam.iseonjwa.performance.entity.Performance;
import secondteam.iseonjwa.performance.entity.PerformanceStatus;
import secondteam.iseonjwa.performance.entity.Price;
import secondteam.iseonjwa.performance.entity.Schedule;
import secondteam.iseonjwa.performance.repository.PerformanceRepository;
import secondteam.iseonjwa.performance.repository.PriceRepository;
import secondteam.iseonjwa.performance.repository.ScheduleRepository;
import secondteam.iseonjwa.reservation.repository.PaymentRepository;
import secondteam.iseonjwa.reservation.repository.ReservationRepository;
import secondteam.iseonjwa.reservation.repository.ReservationSeatRepository;
import secondteam.iseonjwa.seat.entity.Seat;
import secondteam.iseonjwa.seat.entity.SeatSection;
import secondteam.iseonjwa.seat.repository.SeatRepository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:queue-leave-test;MODE=MySQL",
        "queue.active.max-size=4",
        "queue.active.ttl-seconds=600"
})
@AutoConfigureMockMvc
@Import(WaitingQueueControllerTest.TestQueueConfig.class)
class QueueLeaveControllerTest {

    private static final String WAITING_ENDPOINT = "/api/queue/waiting";
    private static final String STATUS_ENDPOINT = "/api/queue/status";
    private static final String ACTIVE_ENDPOINT = "/api/queue/active";
    private static final String LEAVE_ENDPOINT = "/api/queue";
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PerformanceRepository performanceRepository;

    @Autowired
    private PriceRepository priceRepository;

    @Autowired
    private ScheduleRepository scheduleRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private ReservationSeatRepository reservationSeatRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private WaitingQueueControllerTest.InMemoryQueueRepository queueRepository;

    private Performance performance1;
    private Performance performance2;
    private List<Member> members;

    @BeforeEach
    void setUp() {
        queueRepository.clear();
        transactionTemplate.executeWithoutResult(status -> {
            paymentRepository.deleteAllInBatch();
            reservationSeatRepository.deleteAllInBatch();
            reservationRepository.deleteAllInBatch();
            seatRepository.deleteAllInBatch();
            priceRepository.deleteAllInBatch();
            scheduleRepository.deleteAllInBatch();
            performanceRepository.deleteAllInBatch();
            memberRepository.deleteAllInBatch();

            performance1 = performanceRepository.save(Performance.builder()
                    .title("아이유 콘서트")
                    .genre(Genre.K_POP)
                    .runningTime(150)
                    .description("단독 콘서트")
                    .status(PerformanceStatus.ON_SALE)
                    .venueName("잠실주경기장")
                    .maxTicketsPerMember(2)
                    .build());

            performance2 = performanceRepository.save(Performance.builder()
                    .title("재즈 나이트")
                    .genre(Genre.ETC)
                    .runningTime(100)
                    .description("소극장 공연")
                    .status(PerformanceStatus.ON_SALE)
                    .venueName("블루노트")
                    .maxTicketsPerMember(4)
                    .build());

            members = new ArrayList<>();
            for (int i = 1; i <= 8; i++) {
                members.add(memberRepository.save(Member.builder()
                        .email("leave-member" + i + "@test.com")
                        .password("encoded")
                        .name("회원" + i)
                        .phone("0107777" + String.format("%04d", i))
                        .build()));
            }
        });
    }

    @Test
    void removesMemberFromWaitingQueueAdvancesBehindMemberAndAllowsImmediateReentry() throws Exception {
        Long firstMemberId = members.get(0).getMemberId();
        Long secondMemberId = members.get(1).getMemberId();

        enqueueWaiting(firstMemberId, performance1.getPerformanceId());
        enqueueWaiting(secondMemberId, performance1.getPerformanceId());

        mockMvc.perform(delete(LEAVE_ENDPOINT)
                        .header("X-Member-Id", firstMemberId))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"data\":null}"))
                .andExpect(jsonPath("$.data").value(Matchers.nullValue()));

        // 퇴장한 1번 회원은 큐 미존재(404 QUEUE_ENTRY_NOT_FOUND)
        mockMvc.perform(get(STATUS_ENDPOINT)
                        .header("X-Member-Id", firstMemberId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("QUEUE_ENTRY_NOT_FOUND"));

        // 2번 회원의 대기 순번은 2 -> 1로 즉시 당겨짐
        mockMvc.perform(get(STATUS_ENDPOINT)
                        .header("X-Member-Id", secondMemberId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.position").value(1));

        // 퇴장한 1번 회원은 다른 공연 대기열에도 즉시 재진입 가능
        mockMvc.perform(post(WAITING_ENDPOINT)
                        .header("X-Member-Id", firstMemberId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"performanceId": %d}
                                """.formatted(performance2.getPerformanceId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.performanceId").value(performance2.getPerformanceId()))
                .andExpect(jsonPath("$.data.position").value(1));
    }

    @Test
    void removesMemberFromActiveQueueAndFreesSlotImmediatelyForWaitingMember() throws Exception {
        Instant now = Instant.now();
        // 작업열 4석 만석 세팅
        for (int i = 0; i < 4; i++) {
            queueRepository.putActiveForTest(
                    members.get(i).getMemberId(),
                    performance1.getPerformanceId(),
                    now.plusSeconds(600)
            );
        }

        Long waitingMemberId = members.get(4).getMemberId();
        enqueueWaiting(waitingMemberId, performance1.getPerformanceId());

        // 퇴장 전: 만석이므로 대기 1번의 canEnter = false
        mockMvc.perform(get(STATUS_ENDPOINT)
                        .header("X-Member-Id", waitingMemberId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canEnter").value(false));

        // 작업열 회원 1명이 자진 퇴장
        mockMvc.perform(delete(LEAVE_ENDPOINT)
                        .header("X-Member-Id", members.get(0).getMemberId()))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"data\":null}"));

        // 퇴장 직후: 빈자리 1석이 즉시 생겨 대기 1번의 canEnter = true 및 작업열 진입 성공
        mockMvc.perform(get(STATUS_ENDPOINT)
                        .header("X-Member-Id", waitingMemberId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canEnter").value(true));

        mockMvc.perform(post(ACTIVE_ENDPOINT)
                        .header("X-Member-Id", waitingMemberId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"performanceId": %d}
                                """.formatted(performance1.getPerformanceId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void returnsIdempotent200WhenMemberIsNotInQueueOrAlreadyExpired() throws Exception {
        Long unqueuedMemberId = members.get(0).getMemberId();
        Long expiredMemberId = members.get(1).getMemberId();
        queueRepository.putActiveForTest(
                expiredMemberId,
                performance1.getPerformanceId(),
                Instant.now().minusSeconds(10)
        );

        mockMvc.perform(delete(LEAVE_ENDPOINT)
                        .header("X-Member-Id", unqueuedMemberId))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"data\":null}"));

        mockMvc.perform(delete(LEAVE_ENDPOINT)
                        .header("X-Member-Id", expiredMemberId))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"data\":null}"));
    }

    @Test
    void automaticallyRemovesMemberFromActiveQueueWhenReservationIsConfirmed() throws Exception {
        Instant now = Instant.now();
        // 작업열 4석 만석 세팅 (0번 회원이 예매 완료 예정)
        for (int i = 0; i < 4; i++) {
            queueRepository.putActiveForTest(
                    members.get(i).getMemberId(),
                    performance1.getPerformanceId(),
                    now.plusSeconds(600)
            );
        }
        Long buyerId = members.get(0).getMemberId();
        Long waitingMemberId = members.get(4).getMemberId();
        enqueueWaiting(waitingMemberId, performance1.getPerformanceId());

        Long[] ids = transactionTemplate.execute(status -> {
            Performance perf = performanceRepository.findById(performance1.getPerformanceId()).orElseThrow();
            Member buyer = memberRepository.findById(buyerId).orElseThrow();

            priceRepository.save(Price.builder()
                    .performance(perf)
                    .section(SeatSection.S1)
                    .price(120_000)
                    .build());

            Schedule schedule = scheduleRepository.save(Schedule.builder()
                    .performance(perf)
                    .startAt(LocalDateTime.now(ZONE).plusDays(7))
                    .build());

            Seat seat = Seat.builder()
                    .schedule(schedule)
                    .section(SeatSection.S1)
                    .rowNum("1")
                    .seatNum(1)
                    .build();
            seat.hold(buyer, LocalDateTime.now(ZONE).plusMinutes(5));
            Seat savedSeat = seatRepository.save(seat);
            return new Long[]{schedule.getScheduleId(), savedSeat.getSeatId()};
        });

        // 8.4 예매 확정 호출 -> 커밋 직후 active_queue에서 buyer 자동 제거
        mockMvc.perform(post("/api/reservations")
                        .header("X-Member-Id", buyerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "scheduleId": %d,
                                  "seatIds": [%d],
                                  "paymentMethod": "CARD"
                                }
                                """.formatted(ids[0], ids[1])))
                .andExpect(status().isCreated());

        // 예매 완료된 회원은 작업열에서 자동 삭제됨
        mockMvc.perform(get(STATUS_ENDPOINT)
                        .header("X-Member-Id", buyerId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("QUEUE_ENTRY_NOT_FOUND"));

        // 빈자리가 즉시 확보되어 대기 1번 회원의 canEnter = true
        mockMvc.perform(get(STATUS_ENDPOINT)
                        .header("X-Member-Id", waitingMemberId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canEnter").value(true));
    }

    @Test
    void returnsNotFoundOrBadRequestForMissingMemberOrInvalidHeader() throws Exception {
        mockMvc.perform(delete(LEAVE_ENDPOINT)
                        .header("X-Member-Id", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("MEMBER_NOT_FOUND"));

        mockMvc.perform(delete(LEAVE_ENDPOINT))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));

        mockMvc.perform(delete(LEAVE_ENDPOINT)
                        .header("X-Member-Id", "invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
    }

    private void enqueueWaiting(Long memberId, Long performanceId) throws Exception {
        mockMvc.perform(post(WAITING_ENDPOINT)
                        .header("X-Member-Id", memberId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"performanceId": %d}
                                """.formatted(performanceId)))
                .andExpect(status().isCreated());
    }
}
