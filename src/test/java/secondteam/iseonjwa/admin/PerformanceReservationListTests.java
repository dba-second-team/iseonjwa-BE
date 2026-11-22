package secondteam.iseonjwa.admin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:admin-performance-reservations;MODE=MySQL")
@AutoConfigureMockMvc
@Transactional
class PerformanceReservationListTests {
    private static final String ENDPOINT = "/api/admin/performances/{performanceId}/reservations";
    private static final String MEMBER_ID_HEADER = "X-Member-Id";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void createFixtures() {
        jdbc.update("""
                INSERT INTO member (member_id, email, name, role, created_at, password)
                VALUES (1, 'admin@example.test', '관리자', 'ADMIN', CURRENT_TIMESTAMP, 'unused'),
                       (2, 'user@example.test', '예매자', 'USER', CURRENT_TIMESTAMP, 'unused')
                """);
        jdbc.update("""
                INSERT INTO performance (performance_id, title, genre, running_time, status, venue_name,
                                         max_tickets_per_member)
                VALUES (1, '첫 공연', 'K_POP', 90, 'ON_SALE', '공연장', 4),
                       (2, '다른 공연', 'K_POP', 90, 'ON_SALE', '공연장', 4),
                       (3, '예매 없는 공연', 'K_POP', 90, 'UPCOMING', '공연장', 4)
                """);
        jdbc.update("""
                INSERT INTO schedule (schedule_id, start_at, performance_id)
                VALUES (10, '2026-12-14 19:30:00', 1), (11, '2026-12-13 19:30:00', 1),
                       (12, '2026-12-13 19:30:00', 2)
                """);
        jdbc.update("""
                INSERT INTO reservation (reservation_id, status, reserved_at, member_id, schedule_id)
                VALUES (20, 'CONFIRMED', '2026-11-20 10:00:00', 2, 11),
                       (21, 'CONFIRMED', '2026-11-20 10:00:00', 2, 10),
                       (22, 'CANCELED', '2026-11-21 11:50:00', 2, 10),
                       (23, 'CANCELED', '2026-11-21 11:50:00', 2, 10),
                       (24, 'CONFIRMED', '2026-11-22 12:30:00', 2, 12),
                       (25, 'PENDING', '2026-11-22 10:00:00', 2, 10),
                       (26, 'CANCELED', '2026-11-22 10:00:00', 2, 12)
                """);
        jdbc.update("""
                INSERT INTO payment (paid_amount, cancel_fee, refund_amount, status, reservation_id)
                VALUES (45000, 0, 0, 'PAID', 20), (90000, 0, 0, 'PAID', 21),
                       (45000, 5000, 0, 'CANCELED', 22), (45000, 5000, 39000, 'REFUNDED', 23),
                       (45000, 0, 0, 'PAID', 24), (45000, 0, 0, 'CANCELED', 26)
                """);
        jdbc.update("""
                INSERT INTO seat (seat_id, section, row_num, seat_num, status, schedule_id)
                VALUES (100, 'F1', '1', 1, 'RESERVED', 11),
                       (101, 'F1', '2', 7, 'RESERVED', 10),
                       (102, 'F1', '2', 6, 'RESERVED', 10),
                       (103, 'S2', '3', 1, 'AVAILABLE', 10)
                """);
        jdbc.update("""
                INSERT INTO reservation_seat (reservation_id, seat_id)
                VALUES (20, 100), (21, 101), (21, 102), (22, 103)
                """);
    }

    @Test
    void pagesReservationsBeforeLoadingSeatsAndBreaksTiesById() throws Exception {
        mockMvc.perform(get(ENDPOINT, 1).header(MEMBER_ID_HEADER, 1).param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.totalCount").value(2))
                .andExpect(jsonPath("$.data.items[0].reservationId").value(21))
                .andExpect(jsonPath("$.data.items[0].memberName").value("예매자"))
                .andExpect(jsonPath("$.data.items[0].scheduleId").value(10))
                .andExpect(jsonPath("$.data.items[0].scheduleNumber").value(2))
                .andExpect(jsonPath("$.data.items[0].startAt").value("2026-12-14T19:30:00+09:00"))
                .andExpect(jsonPath("$.data.items[0].reservedAt").value("2026-11-20T10:00:00+09:00"))
                .andExpect(jsonPath("$.data.items[0].ticketCount").value(2))
                .andExpect(jsonPath("$.data.items[0].seats[0].section").value("F1"))
                .andExpect(jsonPath("$.data.items[0].seats[0].rowNum").value("2"))
                .andExpect(jsonPath("$.data.items[0].seats[0].seatNum").value(6))
                .andExpect(jsonPath("$.data.items[0].seats[1].seatNum").value(7))
                .andExpect(jsonPath("$.data.items[0].status").value("PAID"))
                .andExpect(jsonPath("$.data.items[0].paidAmount").value(90000))
                .andExpect(jsonPath("$.data.items[0].expectedRefundAmount").value(nullValue()))
                .andExpect(jsonPath("$.data.items[0].refundAmount").value(nullValue()));

        mockMvc.perform(get(ENDPOINT, 1).header(MEMBER_ID_HEADER, 1).param("page", "1").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].reservationId").value(20))
                .andExpect(jsonPath("$.data.items[0].scheduleNumber").value(1))
                .andExpect(jsonPath("$.data.totalCount").value(2));
    }

    @Test
    void limitsTheDefaultPageToTwentyReservations() throws Exception {
        for (int id = 30; id < 49; id++) {
            jdbc.update("""
                    INSERT INTO reservation (reservation_id, status, reserved_at, member_id, schedule_id)
                    VALUES (?, 'CONFIRMED', '2026-11-22 10:00:00', 2, 11)
                    """, id);
            jdbc.update("""
                    INSERT INTO payment (paid_amount, cancel_fee, refund_amount, status, reservation_id)
                    VALUES (45000, 0, 0, 'PAID', ?)
                    """, id);
        }

        mockMvc.perform(get(ENDPOINT, 1).header(MEMBER_ID_HEADER, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.totalCount").value(21))
                .andExpect(jsonPath("$.data.items.length()").value(20))
                .andExpect(jsonPath("$.data.items[0].reservationId").value(48))
                .andExpect(jsonPath("$.data.items[19].reservationId").value(21));

        mockMvc.perform(get(ENDPOINT, 1).header(MEMBER_ID_HEADER, 1).param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].reservationId").value(20))
                .andExpect(jsonPath("$.data.totalCount").value(21));
    }

    @Test
    void distinguishesPendingRefundFromCompletedRefundUsingPaymentStatus() throws Exception {
        mockMvc.perform(get(ENDPOINT, 1).header(MEMBER_ID_HEADER, 1).param("status", "CANCELED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].reservationId").value(22))
                .andExpect(jsonPath("$.data.items[0].status").value("CANCELED"))
                .andExpect(jsonPath("$.data.items[0].seats[0].section").value("S2"))
                .andExpect(jsonPath("$.data.items[0].expectedRefundAmount").value(40000))
                .andExpect(jsonPath("$.data.items[0].refundAmount").value(nullValue()));

        mockMvc.perform(get(ENDPOINT, 1).header(MEMBER_ID_HEADER, 1).param("status", "REFUNDED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].reservationId").value(23))
                .andExpect(jsonPath("$.data.items[0].status").value("REFUNDED"))
                .andExpect(jsonPath("$.data.items[0].seats").isEmpty())
                .andExpect(jsonPath("$.data.items[0].expectedRefundAmount").value(nullValue()))
                .andExpect(jsonPath("$.data.items[0].refundAmount").value(39000));
    }

    @Test
    void distinguishesMissingPerformancesFromEmptyPages() throws Exception {
        mockMvc.perform(get(ENDPOINT, 3).header(MEMBER_ID_HEADER, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.totalCount").value(0));

        mockMvc.perform(get(ENDPOINT, 1).header(MEMBER_ID_HEADER, 1).param("page", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.totalCount").value(2));

        mockMvc.perform(get(ENDPOINT, 999).header(MEMBER_ID_HEADER, 1))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PERFORMANCE_NOT_FOUND"));
    }

    @Test
    void rejectsInvalidParameters() throws Exception {
        mockMvc.perform(get(ENDPOINT, 1).header(MEMBER_ID_HEADER, 1).param("status", "PENDING"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        mockMvc.perform(get(ENDPOINT, 1).header(MEMBER_ID_HEADER, 1).param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        mockMvc.perform(get(ENDPOINT, 1).header(MEMBER_ID_HEADER, 1).param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        mockMvc.perform(get(ENDPOINT, "abc").header(MEMBER_ID_HEADER, 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
    }

    @Test
    void enforcesExistingAdminAccessChecks() throws Exception {
        mockMvc.perform(get(ENDPOINT, 1).header(MEMBER_ID_HEADER, 2))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
        mockMvc.perform(get(ENDPOINT, 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
    }
}
