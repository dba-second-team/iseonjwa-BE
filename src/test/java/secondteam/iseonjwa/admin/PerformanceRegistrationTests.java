package secondteam.iseonjwa.admin;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import secondteam.iseonjwa.seat.repository.ScheduleSeatRepository;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:performance-registration;MODE=MySQL")
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PerformanceRegistrationTests {
    private static final String ENDPOINT = "/api/admin/performances";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoSpyBean
    private ScheduleSeatRepository scheduleSeatRepository;

    private long adminId;
    private long userId;

    @BeforeAll
    void createMembers() {
        adminId = createMember("admin@example.test", "ADMIN");
        userId = createMember("user@example.test", "USER");
    }

    @Test
    void registersPerformanceSchedulesAndAllSectionPrices() throws Exception {
        register(adminId, request("등록 테스트 공연장", """
                {"startAt":"2026-12-13T19:30:00+09:00"},
                {"startAt":"2026-12-12T10:30:00Z"}
                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.performanceId").isNumber())
                .andExpect(jsonPath("$.data.status").value("UPCOMING"))
                .andExpect(jsonPath("$.data.schedules.length()").value(2))
                .andExpect(jsonPath("$.data.schedules[0].startAt").value("2026-12-12T19:30:00+09:00"));

        Long performanceId = jdbc.queryForObject(
                "SELECT performance_id FROM performance WHERE venue_name = ?", Long.class, "등록 테스트 공연장");
        String posterKey = jdbc.queryForObject(
                "SELECT poster_key FROM performance WHERE performance_id = ?", String.class, performanceId);
        List<LocalDateTime> startTimes = jdbc.query("""
                SELECT start_at FROM schedule WHERE performance_id = ? ORDER BY start_at
                """, (row, index) -> row.getTimestamp("start_at").toLocalDateTime(), performanceId);
        List<String> sections = jdbc.queryForList("""
                SELECT section FROM price WHERE performance_id = ?
                """, String.class, performanceId);

        assertThat(posterKey).isNull();
        assertThat(startTimes).containsExactly(
                LocalDateTime.of(2026, 12, 12, 19, 30),
                LocalDateTime.of(2026, 12, 13, 19, 30));
        assertThat(sections).containsExactlyInAnyOrder("F1", "F2", "F3", "F4", "S1", "S2", "G");
        assertThat(jdbc.queryForObject("""
                SELECT price FROM price WHERE performance_id = ? AND section = 'G'
                """, Integer.class, performanceId)).isZero();
    }

    @Test
    void createsEverySeatForFourSchedulesWithTheNumberResetForEachRow() throws Exception {
        String venue = "회차별 자동 좌석 공연장";
        register(adminId, request(venue, """
                {"startAt":"2027-01-01T19:30:00+09:00"},
                {"startAt":"2027-01-02T19:30:00+09:00"},
                {"startAt":"2027-01-03T19:30:00+09:00"},
                {"startAt":"2027-01-04T19:30:00+09:00"}
                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.schedules.length()").value(4));

        record SectionCount(long scheduleId, String section, int seats, int rows, int available) {}
        var sectionCounts = jdbc.query("""
                SELECT s.schedule_id, st.section, COUNT(*) AS seat_count,
                       COUNT(DISTINCT st.row_num) AS row_count,
                       SUM(CASE WHEN st.status = 'AVAILABLE' THEN 1 ELSE 0 END) AS available_count
                FROM performance p
                JOIN schedule s ON s.performance_id = p.performance_id
                JOIN seat st ON st.schedule_id = s.schedule_id
                WHERE p.venue_name = ?
                GROUP BY s.schedule_id, st.section
                """, (row, index) -> new SectionCount(row.getLong("schedule_id"), row.getString("section"),
                row.getInt("seat_count"), row.getInt("row_count"), row.getInt("available_count")), venue);
        var countsBySchedule = sectionCounts.stream().collect(Collectors.groupingBy(SectionCount::scheduleId));

        assertThat(countsBySchedule).hasSize(4);
        assertThat(sectionCounts).hasSize(28).allSatisfy(section -> {
            assertThat(section.seats()).isEqualTo(150);
            assertThat(section.rows()).isEqualTo(15);
            assertThat(section.available()).isEqualTo(150);
        });
        for (var entry : countsBySchedule.entrySet()) {
            assertThat(entry.getValue()).extracting(SectionCount::section)
                    .containsExactlyInAnyOrder("F1", "F2", "F3", "F4", "S1", "S2", "G");

            for (String rowNumber : List.of("1", "8", "15")) {
                assertThat(jdbc.queryForList("""
                        SELECT seat_num FROM seat
                        WHERE schedule_id = ? AND section = 'F2' AND row_num = ?
                        ORDER BY seat_num
                        """, Integer.class, entry.getKey(), rowNumber))
                        .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
            }
        }

        var scheduleIds = countsBySchedule.keySet().stream().sorted().toList();
        jdbc.update("""
                UPDATE seat SET status = 'RESERVED'
                WHERE schedule_id = ? AND section = 'F2' AND row_num = '1' AND seat_num = 3
                """, scheduleIds.getFirst());
        assertThat(jdbc.queryForObject("""
                SELECT status FROM seat
                WHERE schedule_id = ? AND section = 'F2' AND row_num = '1' AND seat_num = 3
                """, String.class, scheduleIds.get(1))).isEqualTo("AVAILABLE");
    }

    @Test
    void rollsBackThePerformanceSchedulesPricesAndSeatsWhenSeatCreationFails() {
        String venue = "좌석 생성 롤백 공연장";
        int existingSchedules = jdbc.queryForObject("SELECT COUNT(*) FROM schedule", Integer.class);
        int existingPrices = jdbc.queryForObject("SELECT COUNT(*) FROM price", Integer.class);
        int existingSeats = jdbc.queryForObject("SELECT COUNT(*) FROM seat", Integer.class);
        var calls = new AtomicInteger();
        doAnswer(invocation -> {
            invocation.callRealMethod();
            if (calls.incrementAndGet() == 2) {
                throw new DataIntegrityViolationException("Seat creation failed");
            }
            return null;
        }).when(scheduleSeatRepository).createForSchedule(anyLong());

        assertThatThrownBy(() -> register(adminId, request(venue, """
                {"startAt":"2027-01-05T19:30:00+09:00"},
                {"startAt":"2027-01-06T19:30:00+09:00"}
                """)))
                .hasRootCauseInstanceOf(DataIntegrityViolationException.class);

        assertThat(calls.get()).isEqualTo(2);
        assertThat(performanceCount(venue)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM schedule", Integer.class)).isEqualTo(existingSchedules);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM price", Integer.class)).isEqualTo(existingPrices);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM seat", Integer.class)).isEqualTo(existingSeats);
    }

    @Test
    void rejectsDuplicateSections() throws Exception {
        String body = request("중복 가격 공연장", """
                {"startAt":"2026-12-13T19:30:00+09:00"}
                """).replace("\"section\":\"G\"", "\"section\":\"S2\"");

        register(adminId, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));

        assertThat(performanceCount("중복 가격 공연장")).isZero();
    }

    @Test
    void rejectsMissingSectionPrices() throws Exception {
        String body = request("가격 누락 공연장", """
                {"startAt":"2026-12-13T19:30:00+09:00"}
                """).replace(",\n    {\"section\":\"G\",\"price\":0}", "");

        register(adminId, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));

        assertThat(performanceCount("가격 누락 공연장")).isZero();
    }

    @Test
    void rejectsOverlappingSchedulesWithinTheRequest() throws Exception {
        register(adminId, request("요청 충돌 공연장", """
                {"startAt":"2026-12-13T19:30:00+09:00"},
                {"startAt":"2026-12-13T20:00:00+09:00"}
                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("SCHEDULE_CONFLICT"));

        assertThat(performanceCount("요청 충돌 공연장")).isZero();
    }

    @Test
    void rejectsOverlapWithOtherPerformancesAtTheSameVenue() throws Exception {
        String venue = "기존 회차 공연장";
        register(adminId, request(venue, """
                {"startAt":"2026-12-13T19:30:00+09:00"}
                """))
                .andExpect(status().isCreated());

        register(adminId, request(venue, """
                {"startAt":"2026-12-13T20:00:00+09:00"}
                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("SCHEDULE_CONFLICT"));

        assertThat(performanceCount(venue)).isEqualTo(1);

        register(adminId, request(venue, """
                {"startAt":"2026-12-13T21:00:00+09:00"}
                """))
                .andExpect(status().isCreated());
    }

    @Test
    void serializesConcurrentRegistrationsAtTheSameVenue() throws Exception {
        String venue = "동시 등록 공연장";
        String body = request(venue, """
                {"startAt":"2026-12-13T19:30:00+09:00"}
                """);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                start.await();
                return register(adminId, body).andReturn().getResponse().getStatus();
            });
            var second = executor.submit(() -> {
                start.await();
                return register(adminId, body).andReturn().getResponse().getStatus();
            });
            start.countDown();

            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        }

        assertThat(performanceCount(venue)).isEqualTo(1);
    }

    @Test
    void rejectsRequestsWithoutAdminPermission() throws Exception {
        mockMvc.perform(multipart(ENDPOINT))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));

        register(userId, request("일반 회원 등록", """
                {"startAt":"2026-12-13T19:30:00+09:00"}
                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));

        assertThat(performanceCount("일반 회원 등록")).isZero();
    }

    @Test
    void returnsTheCommonErrorForAnInvalidPerformancePart() throws Exception {
        mockMvc.perform(multipart(ENDPOINT).header("X-Member-Id", adminId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));

        register(adminId, "{")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
    }

    @Test
    void rejectsAnAttachedPosterBeforeStorageIsAvailable() throws Exception {
        var poster = new MockMultipartFile("poster", "poster.png", "image/png", new byte[]{1});

        mockMvc.perform(multipart(ENDPOINT)
                        .file(performancePart(request("포스터 첨부 공연장", """
                                {"startAt":"2026-12-13T19:30:00+09:00"}
                                """)))
                        .file(poster)
                        .header("X-Member-Id", adminId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));

        assertThat(performanceCount("포스터 첨부 공연장")).isZero();
    }

    private ResultActions register(long memberId, String body) throws Exception {
        return mockMvc.perform(multipart(ENDPOINT).file(performancePart(body)).header("X-Member-Id", memberId));
    }

    private MockMultipartFile performancePart(String body) {
        return new MockMultipartFile("performance", "", MediaType.APPLICATION_JSON_VALUE,
                body.getBytes(StandardCharsets.UTF_8));
    }

    private long createMember(String email, String role) {
        jdbc.update("""
                INSERT INTO member (email, name, role, created_at, password) VALUES (?, ?, ?, ?, ?)
                """, email, "테스트 회원", role, Timestamp.valueOf(LocalDateTime.of(2026, 11, 22, 0, 0)), "unused");
        return jdbc.queryForObject("SELECT member_id FROM member WHERE email = ?", Long.class, email);
    }

    private long performanceCount(String venue) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM performance WHERE venue_name = ?", Long.class, venue);
    }

    private String request(String venue, String schedules) {
        return """
                {
                  "title":"비밀파티",
                  "genre":"K_POP",
                  "venueName":"%s",
                  "runningTime":90,
                  "maxTicketsPerMember":4,
                  "schedules":[%s],
                  "prices":[
                    {"section":"F1","price":45000},
                    {"section":"F2","price":45000},
                    {"section":"F3","price":40000},
                    {"section":"F4","price":40000},
                    {"section":"S1","price":30000},
                    {"section":"S2","price":30000},
                    {"section":"G","price":0}
                  ]
                }
                """.formatted(venue, schedules);
    }
}
