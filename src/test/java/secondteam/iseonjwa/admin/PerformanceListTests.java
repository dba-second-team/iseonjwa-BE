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

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:admin-performance-list;MODE=MySQL")
@AutoConfigureMockMvc
@Transactional
class PerformanceListTests {
    private static final String ENDPOINT = "/api/admin/performances";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void createMembers() {
        jdbc.update("""
                INSERT INTO member (member_id, email, name, role, created_at, password)
                VALUES (1, 'admin@example.test', '관리자', 'ADMIN', CURRENT_TIMESTAMP, 'unused'),
                       (2, 'user@example.test', '일반 회원', 'USER', CURRENT_TIMESTAMP, 'unused')
                """);
    }

    @Test
    void listsPerformanceCardsAndCountsPaidTicketsAcrossSchedules() throws Exception {
        jdbc.update("""
                INSERT INTO performance (performance_id, title, genre, running_time, status, venue_name,
                                         max_tickets_per_member)
                VALUES (1, '첫 공연', 'K_POP', 90, 'ON_SALE', '공연장', 4),
                       (2, '새 공연', 'HIPHOP_RANDB', 90, 'UPCOMING', '공연장', 4)
                """);
        jdbc.update("""
                INSERT INTO schedule (schedule_id, start_at, performance_id)
                VALUES (10, '2026-12-13 19:30:00', 1), (11, '2026-12-14 19:30:00', 1)
                """);
        jdbc.update("""
                INSERT INTO seat (seat_id, section, row_num, seat_num, status, schedule_id)
                VALUES (100, 'F1', '1', 1, 'RESERVED', 10), (101, 'F1', '1', 2, 'RESERVED', 10),
                       (102, 'F1', '1', 1, 'RESERVED', 11), (103, 'F1', '1', 2, 'RESERVED', 11)
                """);
        jdbc.update("""
                INSERT INTO reservation (reservation_id, status, reserved_at, member_id, schedule_id)
                VALUES (20, 'CONFIRMED', CURRENT_TIMESTAMP, 2, 10),
                       (21, 'CONFIRMED', CURRENT_TIMESTAMP, 2, 11),
                       (22, 'CANCELED', CURRENT_TIMESTAMP, 2, 11),
                       (23, 'CANCELED', CURRENT_TIMESTAMP, 2, 10)
                """);
        jdbc.update("""
                INSERT INTO reservation_seat (reservation_id, seat_id)
                VALUES (20, 100), (20, 101), (21, 102), (22, 103), (23, 100)
                """);
        jdbc.update("""
                INSERT INTO payment (paid_amount, cancel_fee, refund_amount, status, reservation_id)
                VALUES (90000, 0, 0, 'PAID', 20), (45000, 0, 0, 'PAID', 21),
                       (45000, 0, 0, 'PAID', 22), (45000, 0, 45000, 'REFUNDED', 23)
                """);

        mockMvc.perform(get(ENDPOINT).header("X-Member-Id", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.totalCount").value(2))
                .andExpect(jsonPath("$.data.items[0].performanceId").value(2))
                .andExpect(jsonPath("$.data.items[0].title").value("새 공연"))
                .andExpect(jsonPath("$.data.items[0].genre").value("HIPHOP_RANDB"))
                .andExpect(jsonPath("$.data.items[0].posterUrl").value(nullValue()))
                .andExpect(jsonPath("$.data.items[0].soldTicketCount").value(0))
                .andExpect(jsonPath("$.data.items[1].performanceId").value(1))
                .andExpect(jsonPath("$.data.items[1].soldTicketCount").value(3));

        mockMvc.perform(get(ENDPOINT).header("X-Member-Id", 1).param("page", "1").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].performanceId").value(1))
                .andExpect(jsonPath("$.data.totalCount").value(2));

        mockMvc.perform(get(ENDPOINT).header("X-Member-Id", 1).param("page", "2").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.totalCount").value(2));
    }

    @Test
    void rejectsNonAdminsAndInvalidPagination() throws Exception {
        mockMvc.perform(get(ENDPOINT).header("X-Member-Id", 2))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));

        mockMvc.perform(get(ENDPOINT).header("X-Member-Id", 1).param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));

        mockMvc.perform(get(ENDPOINT).header("X-Member-Id", 1).param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
    }
}
