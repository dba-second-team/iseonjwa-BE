package secondteam.iseonjwa.reservation.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import secondteam.iseonjwa.member.entity.Member;
import secondteam.iseonjwa.member.repository.MemberRepository;
import secondteam.iseonjwa.performance.entity.Genre;
import secondteam.iseonjwa.performance.entity.Performance;
import secondteam.iseonjwa.performance.entity.PerformanceStatus;
import secondteam.iseonjwa.performance.entity.Price;
import secondteam.iseonjwa.performance.entity.Schedule;
import secondteam.iseonjwa.performance.repository.PriceRepository;
import secondteam.iseonjwa.performance.repository.ScheduleRepository;
import secondteam.iseonjwa.queue.repository.QueueRepository;
import secondteam.iseonjwa.reservation.entity.Payment;
import secondteam.iseonjwa.reservation.entity.PaymentStatus;
import secondteam.iseonjwa.reservation.repository.PaymentRepository;
import secondteam.iseonjwa.reservation.repository.ReservationRepository;
import secondteam.iseonjwa.reservation.repository.ReservationSeatRepository;
import secondteam.iseonjwa.seat.entity.Seat;
import secondteam.iseonjwa.seat.entity.SeatSection;
import secondteam.iseonjwa.seat.entity.SeatStatus;
import secondteam.iseonjwa.seat.repository.SeatRepository;

import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ReservationConfirmServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private EntityManager em;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ScheduleRepository scheduleRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private PriceRepository priceRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private ReservationSeatRepository reservationSeatRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @MockitoBean
    private QueueRepository queueRepository;

    private Member member;
    private Member otherMember;
    private Schedule schedule;
    private Schedule otherSchedule;
    private Seat heldSeat1;
    private Seat heldSeat2;

    @BeforeEach
    void setUp() {
        transactionTemplate.executeWithoutResult(status -> {
            paymentRepository.deleteAllInBatch();
            reservationSeatRepository.deleteAllInBatch();
            reservationRepository.deleteAllInBatch();
            seatRepository.deleteAllInBatch();
            priceRepository.deleteAllInBatch();
            scheduleRepository.deleteAllInBatch();
            em.createQuery("delete from Performance").executeUpdate();
            memberRepository.deleteAllInBatch();

            member = memberRepository.save(Member.builder()
                    .email("user1@test.com")
                    .password("encoded")
                    .name("홍길동")
                    .phone("01012345678")
                    .build());

            otherMember = memberRepository.save(Member.builder()
                    .email("user2@test.com")
                    .password("encoded")
                    .name("김철수")
                    .phone("01099998888")
                    .build());

            Performance performance = Performance.builder()
                    .title("겨울 갈라 콘서트")
                    .genre(Genre.K_POP)
                    .runningTime(120)
                    .status(PerformanceStatus.ON_SALE)
                    .venueName("중앙아트홀")
                    .maxTicketsPerMember(4)
                    .build();
            em.persist(performance);
            em.flush();

            priceRepository.save(Price.builder()
                    .performance(performance)
                    .section(SeatSection.S1)
                    .price(150_000)
                    .build());
            priceRepository.save(Price.builder()
                    .performance(performance)
                    .section(SeatSection.G)
                    .price(100_000)
                    .build());

            schedule = scheduleRepository.save(Schedule.builder()
                    .performance(performance)
                    .startAt(LocalDateTime.now(ZONE).plusDays(10))
                    .build());

            otherSchedule = scheduleRepository.save(Schedule.builder()
                    .performance(performance)
                    .startAt(LocalDateTime.now(ZONE).plusDays(11))
                    .build());

            LocalDateTime validUntil = LocalDateTime.now(ZONE).plusMinutes(5);
            Seat s1 = Seat.builder().schedule(schedule).section(SeatSection.S1).rowNum("1").seatNum(1).build();
            s1.hold(member, validUntil);
            heldSeat1 = seatRepository.save(s1);

            Seat s2 = Seat.builder().schedule(schedule).section(SeatSection.G).rowNum("2").seatNum(3).build();
            s2.hold(member, validUntil);
            heldSeat2 = seatRepository.save(s2);
        });
    }

    @Test
    void 선점한_좌석을_가상_결제하면_예매와_결제가_저장되고_좌석이_RESERVED로_바뀐다() throws Exception {
        // 역순으로 seatIds를 보내도 seat_id 오름차순으로 잠금·응답되는지 함께 검증
        String body = """
                {
                  "scheduleId": %d,
                  "seatIds": [%d, %d],
                  "paymentMethod": "CARD"
                }
                """.formatted(schedule.getScheduleId(), heldSeat2.getSeatId(), heldSeat1.getSeatId());

        mockMvc.perform(post("/api/reservations")
                        .header("X-Member-Id", member.getMemberId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.scheduleId").value(schedule.getScheduleId()))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.seats[0].seatId").value(heldSeat1.getSeatId()))
                .andExpect(jsonPath("$.data.seats[0].section").value("S1"))
                .andExpect(jsonPath("$.data.seats[0].price").value(150_000))
                .andExpect(jsonPath("$.data.seats[1].seatId").value(heldSeat2.getSeatId()))
                .andExpect(jsonPath("$.data.seats[1].section").value("G"))
                .andExpect(jsonPath("$.data.seats[1].price").value(100_000))
                .andExpect(jsonPath("$.data.totalAmount").value(250_000))
                .andExpect(jsonPath("$.data.paidAt").exists());

        Seat updated1 = seatRepository.findById(heldSeat1.getSeatId()).orElseThrow();
        Seat updated2 = seatRepository.findById(heldSeat2.getSeatId()).orElseThrow();
        assertThat(updated1.getStatus()).isEqualTo(SeatStatus.RESERVED);
        assertThat(updated1.getHeldBy()).isNull();
        assertThat(updated1.getHeldUntil()).isNull();
        assertThat(updated2.getStatus()).isEqualTo(SeatStatus.RESERVED);

        assertThat(reservationRepository.count()).isEqualTo(1);
        assertThat(reservationSeatRepository.count()).isEqualTo(2);
        Payment savedPayment = paymentRepository.findAll().getFirst();
        assertThat(savedPayment.getPaidAmount()).isEqualTo(250_000);
        assertThat(savedPayment.getStatus()).isEqualTo(PaymentStatus.PAID);
        verify(queueRepository).leaveQueue(member.getMemberId());
    }

    @Test
    void 본인이_선점하지_않은_좌석이면_HOLD_EXPIRED로_전체_롤백된다() throws Exception {
        String body = """
                {
                  "scheduleId": %d,
                  "seatIds": [%d],
                  "paymentMethod": "BANK_TRANSFER"
                }
                """.formatted(schedule.getScheduleId(), heldSeat1.getSeatId());

        mockMvc.perform(post("/api/reservations")
                        .header("X-Member-Id", otherMember.getMemberId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("HOLD_EXPIRED"));

        assertThat(reservationRepository.count()).isZero();
        assertThat(paymentRepository.count()).isZero();
        assertThat(seatRepository.findById(heldSeat1.getSeatId()).orElseThrow().getStatus())
                .isEqualTo(SeatStatus.HELD);
        verifyNoInteractions(queueRepository);
    }

    @Test
    void 선점_유효시간이_만료된_좌석이면_HOLD_EXPIRED로_전체_롤백된다() throws Exception {
        Long expiredSeatId = transactionTemplate.execute(status -> {
            Seat expired = Seat.builder().schedule(schedule).section(SeatSection.S1).rowNum("5").seatNum(1).build();
            expired.hold(member, LocalDateTime.now(ZONE).minusSeconds(1));
            return seatRepository.save(expired).getSeatId();
        });

        String body = """
                {
                  "scheduleId": %d,
                  "seatIds": [%d, %d],
                  "paymentMethod": "CARD"
                }
                """.formatted(schedule.getScheduleId(), heldSeat1.getSeatId(), expiredSeatId);

        mockMvc.perform(post("/api/reservations")
                        .header("X-Member-Id", member.getMemberId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("HOLD_EXPIRED"));

        // 함께 요청한 유효 좌석(heldSeat1)까지 단일 트랜잭션으로 롤백되어 HELD 상태가 유지되어야 한다.
        assertThat(reservationRepository.count()).isZero();
        assertThat(seatRepository.findById(heldSeat1.getSeatId()).orElseThrow().getStatus())
                .isEqualTo(SeatStatus.HELD);
        verifyNoInteractions(queueRepository);
    }

    @Test
    void 구역_가격이_설정되지_않았으면_PRICE_NOT_SET을_반환한다() throws Exception {
        Long unpricedSeatId = transactionTemplate.execute(status -> {
            Seat f1Seat = Seat.builder().schedule(schedule).section(SeatSection.F1).rowNum("1").seatNum(1).build();
            f1Seat.hold(member, LocalDateTime.now(ZONE).plusMinutes(5));
            return seatRepository.save(f1Seat).getSeatId();
        });

        String body = """
                {
                  "scheduleId": %d,
                  "seatIds": [%d],
                  "paymentMethod": "CARD"
                }
                """.formatted(schedule.getScheduleId(), unpricedSeatId);

        mockMvc.perform(post("/api/reservations")
                        .header("X-Member-Id", member.getMemberId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PRICE_NOT_SET"));
    }

    @Test
    void 다른_회차_좌석이나_중복_좌석이나_허용되지_않은_결제수단이면_INVALID_REQUEST를_반환한다() throws Exception {
        Long otherScheduleSeatId = transactionTemplate.execute(status -> {
            Seat seat = Seat.builder().schedule(otherSchedule).section(SeatSection.S1).rowNum("1").seatNum(1).build();
            seat.hold(member, LocalDateTime.now(ZONE).plusMinutes(5));
            return seatRepository.save(seat).getSeatId();
        });

        // 1) 다른 회차 소속 좌석
        mockMvc.perform(post("/api/reservations")
                        .header("X-Member-Id", member.getMemberId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scheduleId": %d, "seatIds": [%d], "paymentMethod": "CARD"}
                                """.formatted(schedule.getScheduleId(), otherScheduleSeatId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));

        // 2) 중복 좌석 ID
        mockMvc.perform(post("/api/reservations")
                        .header("X-Member-Id", member.getMemberId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scheduleId": %d, "seatIds": [%d, %d], "paymentMethod": "CARD"}
                                """.formatted(schedule.getScheduleId(), heldSeat1.getSeatId(), heldSeat1.getSeatId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));

        // 3) 허용되지 않은 paymentMethod
        mockMvc.perform(post("/api/reservations")
                        .header("X-Member-Id", member.getMemberId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scheduleId": %d, "seatIds": [%d], "paymentMethod": "CASH"}
                                """.formatted(schedule.getScheduleId(), heldSeat1.getSeatId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
    }

    @Test
    void 존재하지_않는_회원이나_회차면_404를_반환한다() throws Exception {
        mockMvc.perform(post("/api/reservations")
                        .header("X-Member-Id", 999_999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scheduleId": %d, "seatIds": [%d], "paymentMethod": "CARD"}
                                """.formatted(schedule.getScheduleId(), heldSeat1.getSeatId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("MEMBER_NOT_FOUND"));

        mockMvc.perform(post("/api/reservations")
                        .header("X-Member-Id", member.getMemberId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scheduleId": 999999, "seatIds": [%d], "paymentMethod": "CARD"}
                                """.formatted(heldSeat1.getSeatId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("SCHEDULE_NOT_FOUND"));
    }

    @Test
    void 총_결제_금액이_int_범위를_초과하면_INVALID_REQUEST로_롤백된다() throws Exception {
        Long[] expensiveSeatIds = transactionTemplate.execute(status -> {
            Performance expensivePerf = scheduleRepository.findByIdWithPerformance(schedule.getScheduleId())
                    .orElseThrow()
                    .getPerformance();
            priceRepository.save(Price.builder()
                    .performance(expensivePerf)
                    .section(SeatSection.F2)
                    .price(1_500_000_000)
                    .build());

            LocalDateTime validUntil = LocalDateTime.now(ZONE).plusMinutes(5);
            Seat s1 = Seat.builder().schedule(schedule).section(SeatSection.F2).rowNum("1").seatNum(1).build();
            s1.hold(member, validUntil);
            Seat s2 = Seat.builder().schedule(schedule).section(SeatSection.F2).rowNum("1").seatNum(2).build();
            s2.hold(member, validUntil);
            return new Long[]{seatRepository.save(s1).getSeatId(), seatRepository.save(s2).getSeatId()};
        });

        mockMvc.perform(post("/api/reservations")
                        .header("X-Member-Id", member.getMemberId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scheduleId": %d, "seatIds": [%d, %d], "paymentMethod": "CARD"}
                                """.formatted(schedule.getScheduleId(), expensiveSeatIds[0], expensiveSeatIds[1])))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));

        assertThat(reservationRepository.count()).isZero();
        assertThat(paymentRepository.count()).isZero();
    }
}
