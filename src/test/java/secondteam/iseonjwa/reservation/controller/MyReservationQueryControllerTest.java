package secondteam.iseonjwa.reservation.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.net.URI;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:my-reservations-test;MODE=MySQL",
        "app.s3.bucket=test-posters"
})
@AutoConfigureMockMvc
class MyReservationQueryControllerTest {

    private static final String ENDPOINT = "/api/members/me/reservations";
    private static final String POSTER_URL =
            "https://test-posters.s3.ap-northeast-2.amazonaws.com/posters/iu.png?signature=example";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private S3Presigner s3Presigner;

    @BeforeEach
    void setUp() throws Exception {
        var signed = mock(PresignedGetObjectRequest.class);
        when(signed.url()).thenReturn(URI.create(POSTER_URL).toURL());
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(signed);

        jdbc.update("DELETE FROM payment");
        jdbc.update("DELETE FROM reservation_seat");
        jdbc.update("DELETE FROM reservation");
        jdbc.update("DELETE FROM seat");
        jdbc.update("DELETE FROM price");
        jdbc.update("DELETE FROM schedule");
        jdbc.update("DELETE FROM performance");
        jdbc.update("DELETE FROM member");

        jdbc.update("""
                INSERT INTO member (member_id, email, name, phone, role, created_at, password)
                VALUES (2001, 'buyer@example.test', '예매자', '01011112222', 'USER', CURRENT_TIMESTAMP, 'encoded')
                """);

        jdbc.update("""
                INSERT INTO performance (performance_id, title, genre, running_time, status, venue_name, max_tickets_per_member, poster_key)
                VALUES (3001, '아이유 콘서트', 'K_POP', 150, 'ON_SALE', '잠실주경기장', 4, 'posters/iu.png'),
                       (3002, '재즈 나이트', 'ETC', 100, 'ON_SALE', '블루노트', 4, NULL)
                """);

        // schedule_id 순서와 start_at 순서가 달라도 각 공연 내 start_at 오름차순 기준으로 회차(scheduleNumber)가 계산되어야 함
        // 공연 3001: 4002(12/10 19:00 -> 1회차), 4003(12/11 14:00 -> 2회차), 4001(12/11 19:00 -> 3회차)
        // 공연 3002: 4004(12/05 18:00 -> 1회차), 4005(12/06 18:00 -> 2회차)
        jdbc.update("""
                INSERT INTO schedule (schedule_id, start_at, performance_id)
                VALUES (4001, '2026-12-11 19:00:00', 3001),
                       (4002, '2026-12-10 19:00:00', 3001),
                       (4003, '2026-12-11 14:00:00', 3001),
                       (4004, '2026-12-05 18:00:00', 3002),
                       (4005, '2026-12-06 18:00:00', 3002)
                """);

        jdbc.update("""
                INSERT INTO seat (seat_id, section, row_num, seat_num, status, schedule_id)
                VALUES (5001, 'S1', '1', 1, 'RESERVED', 4001),
                       (5002, 'F1', '2', 3, 'AVAILABLE', 4002),
                       (5003, 'G', '5', 10, 'RESERVED', 4005)
                """);

        jdbc.update("""
                INSERT INTO reservation (reservation_id, status, reserved_at, canceled_at, member_id, schedule_id)
                VALUES (6001, 'CONFIRMED', '2026-11-20 10:00:00', NULL, 2001, 4001),
                       (6002, 'CANCELED',  '2026-11-21 12:00:00', '2026-11-21 13:00:00', 2001, 4002),
                       (6003, 'CONFIRMED', '2026-11-22 15:00:00', NULL, 2001, 4005)
                """);

        jdbc.update("""
                INSERT INTO reservation_seat (reservation_id, seat_id)
                VALUES (6001, 5001),
                       (6002, 5002),
                       (6003, 5003)
                """);

        jdbc.update("""
                INSERT INTO payment (payment_id, paid_amount, status, paid_at, cancel_fee, refund_amount, reservation_id)
                VALUES (7001, 150000, 'PAID',     '2026-11-20 10:00:00', 0,     0,     6001),
                       (7002, 180000, 'CANCELED', '2026-11-21 12:00:00', 18000, 162000, 6002),
                       (7003, 90000,  'PAID',     '2026-11-22 15:00:00', 0,     0,     6003)
                """);
    }

    @Test
    void calculatesScheduleNumberPerPerformanceByStartAtOrder() throws Exception {
        // 최신 예매순(6003 -> 6002 -> 6001)으로 반환되며 각각 소속 공연 내 startAt 순서 기반 회차를 반환해야 함
        mockMvc.perform(get(ENDPOINT)
                        .header("X-Member-Id", 2001L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(3))
                // 1) 예매 6003 -> 공연 3002의 4005 회차 (2회차)
                .andExpect(jsonPath("$.data.items[0].reservationId").value(6003))
                .andExpect(jsonPath("$.data.items[0].scheduleId").value(4005))
                .andExpect(jsonPath("$.data.items[0].scheduleNumber").value(2))
                .andExpect(jsonPath("$.data.items[0].performanceTitle").value("재즈 나이트"))
                // 2) 예매 6002 -> 공연 3001의 4002 회차 (가장 이른 12/10 -> 1회차)
                .andExpect(jsonPath("$.data.items[1].reservationId").value(6002))
                .andExpect(jsonPath("$.data.items[1].scheduleId").value(4002))
                .andExpect(jsonPath("$.data.items[1].scheduleNumber").value(1))
                .andExpect(jsonPath("$.data.items[1].paymentStatus").value("CANCELED"))
                // 3) 예매 6001 -> 공연 3001의 4001 회차 (가장 늦은 12/11 19:00 -> 3회차)
                .andExpect(jsonPath("$.data.items[2].reservationId").value(6001))
                .andExpect(jsonPath("$.data.items[2].scheduleId").value(4001))
                .andExpect(jsonPath("$.data.items[2].scheduleNumber").value(3))
                .andExpect(jsonPath("$.data.items[2].performanceTitle").value("아이유 콘서트"));
    }

    @Test
    void returnsPosterUrlOnlyWhenPerformanceHasPoster() throws Exception {
        mockMvc.perform(get(ENDPOINT)
                        .header("X-Member-Id", 2001L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].posterUrl").value(nullValue()))
                .andExpect(jsonPath("$.data.items[1].posterUrl").value(POSTER_URL))
                .andExpect(jsonPath("$.data.items[2].posterUrl").value(POSTER_URL));
    }

    @Test
    void returnsNullPosterUrlWhenPresignFails() throws Exception {
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .thenThrow(SdkClientException.create("down"));

        mockMvc.perform(get(ENDPOINT)
                        .header("X-Member-Id", 2001L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(3))
                .andExpect(jsonPath("$.data.items[2].posterUrl").value(nullValue()));
    }
}
