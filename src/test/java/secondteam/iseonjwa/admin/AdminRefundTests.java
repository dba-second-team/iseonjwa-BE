package secondteam.iseonjwa.admin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:admin-performance-refunds;MODE=MySQL")
@AutoConfigureMockMvc
class AdminRefundTests {
    private static final String ENDPOINT = "/api/admin/performances/{performanceId}/refunds";
    private static final String ADMIN_HEADER = "X-Member-Id";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void createFixtures() {
        jdbc.update("DELETE FROM payment WHERE reservation_id BETWEEN 1401 AND 1406");
        jdbc.update("DELETE FROM reservation_seat WHERE reservation_id BETWEEN 1401 AND 1406");
        jdbc.update("DELETE FROM reservation WHERE reservation_id BETWEEN 1401 AND 1406");
        jdbc.update("DELETE FROM seat WHERE seat_id BETWEEN 1301 AND 1305");
        jdbc.update("DELETE FROM schedule WHERE schedule_id BETWEEN 1201 AND 1203");
        jdbc.update("DELETE FROM performance WHERE performance_id IN (1101, 1102)");
        jdbc.update("DELETE FROM member WHERE member_id BETWEEN 1001 AND 1003");

        jdbc.update("""
                INSERT INTO member (member_id, email, name, role, created_at, password)
                VALUES (1001, 'refund-admin@example.test', '관리자', 'ADMIN', CURRENT_TIMESTAMP, 'unused'),
                       (1002, 'refund-user@example.test', '예매자', 'USER', CURRENT_TIMESTAMP, 'unused'),
                       (1003, 'refund-new-user@example.test', '새 예매자', 'USER', CURRENT_TIMESTAMP, 'unused')
                """);
        jdbc.update("""
                INSERT INTO performance (performance_id, title, genre, running_time, status, venue_name,
                                         max_tickets_per_member)
                VALUES (1101, '첫 공연', 'K_POP', 90, 'ON_SALE', '공연장', 4),
                       (1102, '다른 공연', 'K_POP', 90, 'ON_SALE', '공연장', 4)
                """);
        jdbc.update("""
                INSERT INTO schedule (schedule_id, start_at, performance_id)
                VALUES (1201, '2026-12-13 19:30:00', 1101), (1202, '2026-12-14 19:30:00', 1101),
                       (1203, '2026-12-13 19:30:00', 1102)
                """);
        jdbc.update("""
                INSERT INTO seat (seat_id, section, row_num, seat_num, status, schedule_id)
                VALUES (1301, 'F1', '1', 1, 'AVAILABLE', 1201), (1302, 'F1', '1', 2, 'AVAILABLE', 1201),
                       (1303, 'F1', '1', 1, 'AVAILABLE', 1202), (1304, 'F1', '1', 3, 'RESERVED', 1201),
                       (1305, 'F1', '1', 1, 'AVAILABLE', 1203)
                """);
        jdbc.update("""
                INSERT INTO reservation (reservation_id, status, reserved_at, member_id, schedule_id)
                VALUES (1401, 'CANCELED', CURRENT_TIMESTAMP, 1002, 1201),
                       (1402, 'CANCELED', CURRENT_TIMESTAMP, 1002, 1202),
                       (1403, 'CONFIRMED', CURRENT_TIMESTAMP, 1002, 1201),
                       (1404, 'CANCELED', CURRENT_TIMESTAMP, 1002, 1203),
                       (1405, 'CANCELED', CURRENT_TIMESTAMP, 1002, 1201)
                """);
        jdbc.update("""
                INSERT INTO reservation_seat (reservation_id, seat_id)
                VALUES (1401, 1301), (1401, 1302), (1402, 1303), (1403, 1304), (1404, 1305), (1405, 1301)
                """);
        jdbc.update("""
                INSERT INTO payment (paid_amount, cancel_fee, refund_amount, status, reservation_id)
                VALUES (90000, 10000, 0, 'CANCELED', 1401), (45000, 45000, 0, 'CANCELED', 1402),
                       (45000, 0, 0, 'PAID', 1403), (45000, 0, 0, 'CANCELED', 1404),
                       (45000, 0, 45000, 'REFUNDED', 1405)
                """);
    }

    @Test
    void refundsSelectedReservationsWithoutChangingSeatsOrReservationHistory() throws Exception {
        mockMvc.perform(refund(1101, 1001, """
                        {"reservationIds":[1402,1401]}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].reservationId").value(1402))
                .andExpect(jsonPath("$.data.items[0].refundAmount").value(0))
                .andExpect(jsonPath("$.data.items[1].reservationId").value(1401))
                .andExpect(jsonPath("$.data.items[1].status").value("REFUNDED"))
                .andExpect(jsonPath("$.data.items[1].refundAmount").value(80000))
                .andExpect(jsonPath("$.data.items[1].refundedAt").isString());

        assertThat(jdbc.queryForList("""
                SELECT status FROM payment WHERE reservation_id BETWEEN 1401 AND 1405 ORDER BY reservation_id
                """, String.class)).containsExactly("REFUNDED", "REFUNDED", "PAID", "CANCELED", "REFUNDED");
        assertThat(jdbc.queryForObject("SELECT refund_amount FROM payment WHERE reservation_id = 1401", Integer.class))
                .isEqualTo(80000);
        assertThat(jdbc.queryForObject("SELECT refunded_at FROM payment WHERE reservation_id = 1401",
                LocalDateTime.class)).isNotNull();
        assertThat(jdbc.queryForList("SELECT status FROM seat WHERE seat_id BETWEEN 1301 AND 1305 ORDER BY seat_id",
                String.class)).containsExactly("AVAILABLE", "AVAILABLE", "AVAILABLE", "RESERVED", "AVAILABLE");
        assertThat(jdbc.queryForList("SELECT seat_id FROM reservation_seat WHERE reservation_id = 1401 ORDER BY seat_id",
                Long.class)).containsExactly(1301L, 1302L);
        assertThat(jdbc.queryForObject("SELECT status FROM reservation WHERE reservation_id = 1401", String.class))
                .isEqualTo("CANCELED");
    }

    @Test
    void rejectsAnEntireBatchWhenOneReservationIsNotPending() throws Exception {
        mockMvc.perform(refund(1101, 1001, """
                        {"reservationIds":[1401,1403]}
                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("REFUND_NOT_PENDING"));

        assertPending();
    }

    @Test
    void rejectsReservationsFromAnotherPerformanceAndUnknownPerformances() throws Exception {
        mockMvc.perform(refund(1101, 1001, """
                        {"reservationIds":[1401,1404]}
                        """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESERVATION_NOT_FOUND"));
        mockMvc.perform(refund(9999, 1001, """
                        {"reservationIds":[1401]}
                        """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PERFORMANCE_NOT_FOUND"));

        assertPending();
    }

    @Test
    void refundsOriginalReservationWithoutReleasingItsResoldSeat() throws Exception {
        jdbc.update("UPDATE seat SET status = 'RESERVED' WHERE seat_id = 1301");
        jdbc.update("""
                INSERT INTO reservation (reservation_id, status, reserved_at, member_id, schedule_id)
                VALUES (1406, 'CONFIRMED', CURRENT_TIMESTAMP, 1003, 1201)
                """);
        jdbc.update("INSERT INTO reservation_seat (reservation_id, seat_id) VALUES (1406, 1301)");
        jdbc.update("""
                INSERT INTO payment (paid_amount, cancel_fee, refund_amount, status, reservation_id)
                VALUES (45000, 0, 0, 'PAID', 1406)
                """);

        mockMvc.perform(refund(1101, 1001, """
                        {"reservationIds":[1401]}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].status").value("REFUNDED"));

        assertThat(jdbc.queryForObject("SELECT status FROM seat WHERE seat_id = 1301", String.class))
                .isEqualTo("RESERVED");
        assertThat(jdbc.queryForList("""
                SELECT status FROM payment WHERE reservation_id IN (1401, 1406) ORDER BY reservation_id
                """, String.class)).containsExactly("REFUNDED", "PAID");
    }

    @Test
    void rejectsAnEntireBatchWhenOneReservationHasAnInvalidCancelFee() throws Exception {
        jdbc.update("UPDATE payment SET cancel_fee = 45001 WHERE reservation_id = 1402");

        mockMvc.perform(refund(1101, 1001, """
                        {"reservationIds":[1401,1402]}
                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REFUND_AMOUNT"));

        assertPending();
        assertThat(jdbc.queryForObject("SELECT refunded_at FROM payment WHERE reservation_id = 1401",
                LocalDateTime.class)).isNull();
    }

    @Test
    void rejectsARepeatedRefundRequest() throws Exception {
        mockMvc.perform(refund(1101, 1001, """
                        {"reservationIds":[1401]}
                        """))
                .andExpect(status().isOk());

        mockMvc.perform(refund(1101, 1001, """
                        {"reservationIds":[1401]}
                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("REFUND_NOT_PENDING"));
    }

    @Test
    void onlyOneOfTwoConcurrentRequestsCanCompleteTheSameRefund() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> requestAfter(start));
            var second = executor.submit(() -> requestAfter(start));
            start.countDown();
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        }
        assertThat(jdbc.queryForObject("SELECT refund_amount FROM payment WHERE reservation_id = 1401", Integer.class))
                .isEqualTo(80000);
        assertThat(jdbc.queryForObject("SELECT status FROM payment WHERE reservation_id = 1401", String.class))
                .isEqualTo("REFUNDED");
    }

    @Test
    void validatesBatchInputAndAdminRole() throws Exception {
        for (String body : List.of("""
                {"reservationIds":[]}
                """, """
                {"reservationIds":[null]}
                """, """
                {"reservationIds":[0]}
                """, """
                {"reservationIds":[1401,1401]}
                """, """
                {"reservationIds":1401}
                """)) {
            mockMvc.perform(refund(1101, 1001, body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        }
        mockMvc.perform(refund(1101, 1002, """
                        {"reservationIds":[1401]}
                        """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));

        assertPending();
    }

    private MockHttpServletRequestBuilder refund(long performanceId, long memberId, String body) {
        return post(ENDPOINT, performanceId)
                .header(ADMIN_HEADER, memberId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private int requestAfter(CountDownLatch start) throws Exception {
        start.await();
        return mockMvc.perform(refund(1101, 1001, """
                {"reservationIds":[1401]}
                """)).andReturn().getResponse().getStatus();
    }

    private void assertPending() {
        assertThat(jdbc.queryForList("""
                SELECT status FROM payment WHERE reservation_id IN (1401, 1402) ORDER BY reservation_id
                """, String.class)).containsExactly("CANCELED", "CANCELED");
    }
}
