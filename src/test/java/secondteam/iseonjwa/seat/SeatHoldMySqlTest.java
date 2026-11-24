package secondteam.iseonjwa.seat;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@EnabledIfEnvironmentVariable(named = "ISEONJWA_MYSQL_TEST_URL", matches = ".+")
@SpringBootTest(properties = {
        "spring.datasource.username=${ISEONJWA_MYSQL_TEST_USER:root}",
        "spring.datasource.password=${ISEONJWA_MYSQL_TEST_PASSWORD:}",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "logging.level.org.hibernate.SQL=DEBUG"
})
class SeatHoldMySqlTest {
    private static final String HOLD_PATH = "/api/schedules/{scheduleId}/seats/hold";
    private static final String MEMBER_HEADER = "X-Member-Id";
    private static final StoredSeat AVAILABLE = new StoredSeat("AVAILABLE", null, null);

    @Autowired private WebApplicationContext context;
    @Autowired private JdbcTemplate jdbc;

    private MockMvc mvc;
    private long firstMember;
    private long secondMember;
    private long schedule;
    private long otherSchedule;
    private List<Long> seats;
    private int originalLimit;

    @DynamicPropertySource
    static void useIsolatedMySql(DynamicPropertyRegistry registry) {
        String url = System.getenv("ISEONJWA_MYSQL_TEST_URL");
        if (!url.matches("jdbc:mysql://(?:127\\.0\\.0\\.1|localhost)(?::[0-9]+)?/iseonjwa_hold_test(?:\\?.*)?")) {
            throw new IllegalArgumentException("Use a local, disposable MySQL database named iseonjwa_hold_test");
        }
        registry.add("spring.datasource.url", () -> url);
    }

    @BeforeEach
    void useFlywaySampleData() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        List<Long> members = jdbc.queryForList("select member_id from member order by member_id limit 2", Long.class);
        firstMember = members.get(0);
        secondMember = members.get(1);
        List<Long> schedules = jdbc.queryForList("select schedule_id from schedule order by schedule_id limit 2", Long.class);
        otherSchedule = schedules.get(0);
        // V3의 두 번째 회차에는 확정 예매가 없어 선점마다 독립적으로 검증할 수 있다.
        schedule = schedules.get(1);
        seats = jdbc.queryForList("select seat_id from seat where schedule_id = ? order by seat_id", Long.class, schedule);
        originalLimit = jdbc.queryForObject("""
                select p.max_tickets_per_member from performance p join schedule s using (performance_id)
                where s.schedule_id = ?
                """, Integer.class, schedule);
        resetSeats();
    }

    @AfterEach
    void restoreSampleData() {
        if (seats != null) {
            resetSeats();
            setLimit(originalLimit);
        }
    }

    @Test
    void holdsAllRequestedSeatsWithTheSameOwnerAndExpiration() throws Exception {
        long first = seats.get(1);
        long second = seats.get(0);
        LocalDateTime earliest = LocalDateTime.now(ZoneId.of("Asia/Seoul")).withNano(0).plusMinutes(5);

        MvcResult result = hold(firstMember, schedule, "[%d,%d]".formatted(first, second))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.seatIds[0]").value((int) first))
                .andExpect(jsonPath("$.data.seatIds[1]").value((int) second))
                .andExpect(jsonPath("$.data.heldUntil").isString()).andReturn();

        StoredSeat stored = storedSeat(first);
        assertThat(stored.status()).isEqualTo("HELD");
        assertThat(stored.member()).isEqualTo(firstMember);
        assertThat(stored.until()).isBetween(earliest, LocalDateTime.now(ZoneId.of("Asia/Seoul")).plusMinutes(5));
        assertThat(stored.until()).isEqualTo(LocalDateTime.parse(JsonPath.read(
                result.getResponse().getContentAsString(), "$.data.heldUntil")));
        assertThat(storedSeat(second)).isEqualTo(stored);
    }

    @Test
    void onlyOneMemberWinsTheSameSeat() throws Exception {
        long seat = seats.getFirst();
        List<Outcome> outcomes = race(firstMember, seat, secondMember, seat);

        assertThat(outcomes).extracting(Outcome::status).containsExactlyInAnyOrder(200, 409);
        Outcome winner = outcomes.stream().filter(outcome -> outcome.status() == 200).findFirst().orElseThrow();
        assertThat(outcomes).filteredOn(outcome -> outcome.status() == 409)
                .extracting(Outcome::errorCode).containsExactly("SEAT_UNAVAILABLE");
        assertThat(storedSeat(seat).member()).isEqualTo(winner.member());
        assertThat(storedSeat(seat).status()).isEqualTo("HELD");
    }

    @Test
    void differentMembersCanHoldDifferentSeats() throws Exception {
        long first = seats.get(0);
        long second = seats.get(1);

        assertThat(race(firstMember, first, secondMember, second)).extracting(Outcome::status).containsExactly(200, 200);
        assertThat(storedSeat(first).member()).isEqualTo(firstMember);
        assertThat(storedSeat(second).member()).isEqualTo(secondMember);
    }

    @Test
    void unavailableSeatLeavesTheWholeBatchUnchanged() throws Exception {
        long available = seats.get(0);
        long reserved = seats.get(1);
        jdbc.update("update seat set status = 'RESERVED' where seat_id = ?", reserved);
        StoredSeat before = storedSeat(reserved);

        hold(firstMember, schedule, "[%d,%d]".formatted(available, reserved))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("SEAT_UNAVAILABLE"));

        assertThat(storedSeat(available)).isEqualTo(AVAILABLE);
        assertThat(storedSeat(reserved)).isEqualTo(before);
    }

    @Test
    void unavailableSeatsTakePriorityOverMemberAndLimitErrors() throws Exception {
        long available = seats.get(0);
        long reserved = seats.get(1);
        setLimit(1);
        jdbc.update("update seat set status = 'RESERVED' where seat_id = ?", reserved);
        StoredSeat before = storedSeat(reserved);
        String request = "[%d,%d]".formatted(available, reserved);

        for (long member : List.of(firstMember, Long.MAX_VALUE)) {
            hold(member, schedule, request)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("SEAT_UNAVAILABLE"));
        }

        assertThat(storedSeat(available)).isEqualTo(AVAILABLE);
        assertThat(storedSeat(reserved)).isEqualTo(before);
    }

    @Test
    void liveHoldConflictsAndExpiredHoldCanBeAcquired() throws Exception {
        long seat = seats.getFirst();
        hold(firstMember, schedule, List.of(seat).toString()).andExpect(status().isOk());
        StoredSeat live = storedSeat(seat);
        hold(secondMember, schedule, List.of(seat).toString())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("SEAT_UNAVAILABLE"));
        assertThat(storedSeat(seat)).isEqualTo(live);

        LocalDateTime expired = LocalDateTime.now(ZoneId.of("Asia/Seoul")).minusMinutes(1);
        jdbc.update("update seat set held_until = ? where seat_id = ?", expired, seat);
        hold(secondMember, schedule, List.of(seat).toString()).andExpect(status().isOk());
        assertThat(storedSeat(seat).member()).isEqualTo(secondMember);
        assertThat(storedSeat(seat).until()).isAfter(expired);
    }

    @Test
    void holdingAgainReplacesThePreviousHoldInTheSameSchedule() throws Exception {
        setLimit(2);
        long first = seats.get(0);
        long second = seats.get(1);
        long third = seats.get(2);
        hold(firstMember, schedule, "[%d,%d]".formatted(first, second)).andExpect(status().isOk());

        hold(firstMember, schedule, "[%d,%d]".formatted(second, third)).andExpect(status().isOk());

        assertThat(storedSeat(first)).isEqualTo(AVAILABLE);
        assertThat(storedSeat(second).member()).isEqualTo(firstMember);
        assertThat(storedSeat(third).member()).isEqualTo(firstMember);
        assertThat(storedSeat(third)).isEqualTo(storedSeat(second));
    }

    @Test
    void failedHoldKeepsThePreviousHold() throws Exception {
        long held = seats.get(0);
        long reserved = seats.get(1);
        hold(firstMember, schedule, List.of(held).toString()).andExpect(status().isOk());
        StoredSeat before = storedSeat(held);
        jdbc.update("update seat set status = 'RESERVED' where seat_id = ?", reserved);

        hold(firstMember, schedule, List.of(reserved).toString())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("SEAT_UNAVAILABLE"));

        assertThat(storedSeat(held)).isEqualTo(before);
    }

    @Test
    void concurrentRequestsByOneMemberLeaveOnlyTheLastSelection() throws Exception {
        setLimit(1);
        long first = seats.get(0);
        long second = seats.get(1);

        List<Outcome> outcomes = race(firstMember, first, firstMember, second);

        assertThat(outcomes).extracting(Outcome::status).containsExactly(200, 200);
        assertThat(List.of(storedSeat(first).status(), storedSeat(second).status()))
                .containsExactlyInAnyOrder("HELD", "AVAILABLE");
    }

    @Test
    void confirmedSeatsCountTowardTheLimit() throws Exception {
        setLimit(1);
        long member = jdbc.queryForObject("""
                select member_id from reservation where schedule_id = ? and status = 'CONFIRMED' limit 1
                """, Long.class, otherSchedule);
        long seat = jdbc.queryForObject("""
                select seat_id from seat where schedule_id = ? and status = 'AVAILABLE' limit 1
                """, Long.class, otherSchedule);

        hold(member, otherSchedule, List.of(seat).toString())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("HOLD_LIMIT_EXCEEDED"));
        assertThat(storedSeat(seat)).isEqualTo(AVAILABLE);
    }

    @Test
    void rejectsInvalidRequestsWithoutChangingSeats() throws Exception {
        long seat = seats.getFirst();
        long otherSeat = jdbc.queryForObject("select min(seat_id) from seat where schedule_id = ?", Long.class, otherSchedule);
        for (String invalid : List.of("[]", "[null]", "[0]", "[\"" + seat + "\"]", "[" + seat + ".5]",
                "[" + Long.MAX_VALUE + "]", "[9223372036854775808]",
                "[%d,%d]".formatted(seat, seat), "[%d,%d]".formatted(seat, otherSeat))) {
            hold(firstMember, schedule, invalid).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        }
        hold(Long.MAX_VALUE, schedule, List.of(seat).toString()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("MEMBER_NOT_FOUND"));
        hold(firstMember, Long.MAX_VALUE, List.of(seat).toString()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("SCHEDULE_NOT_FOUND"));
        hold(-1, schedule, List.of(seat).toString()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        mvc.perform(post(HOLD_PATH, schedule).contentType("application/json").content("{\"seatIds\":[" + seat + "]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        assertThat(storedSeat(seat)).isEqualTo(AVAILABLE);
    }

    private ResultActions hold(long member, long scheduleId, String seatIds) throws Exception {
        return mvc.perform(post(HOLD_PATH, scheduleId).header(MEMBER_HEADER, member)
                .contentType("application/json").content("{\"seatIds\":" + seatIds + "}"));
    }

    private List<Outcome> race(long firstMemberId, long firstSeat, long secondMemberId, long secondSeat) throws Exception {
        ExecutorService requests = Executors.newFixedThreadPool(2);
        CyclicBarrier start = new CyclicBarrier(2);
        try {
            Future<Outcome> first = requests.submit(() -> requestAtBarrier(start, firstMemberId, firstSeat));
            Future<Outcome> second = requests.submit(() -> requestAtBarrier(start, secondMemberId, secondSeat));
            return List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
        } finally {
            requests.shutdownNow();
        }
    }

    private Outcome requestAtBarrier(CyclicBarrier start, long member, long seat) throws Exception {
        start.await(5, TimeUnit.SECONDS);
        MvcResult result = hold(member, schedule, List.of(seat).toString()).andReturn();
        return new Outcome(member, result.getResponse().getStatus(), result.getResponse().getContentAsString());
    }

    private StoredSeat storedSeat(long seat) {
        return jdbc.queryForObject("select status, held_by, held_until from seat where seat_id = ?", (rs, row) ->
                new StoredSeat(rs.getString("status"), rs.getObject("held_by", Long.class),
                        rs.getObject("held_until", LocalDateTime.class)), seat);
    }

    private void resetSeats() {
        jdbc.update("update seat set status = 'AVAILABLE', held_by = null, held_until = null where schedule_id = ?", schedule);
    }

    private void setLimit(int limit) {
        jdbc.update("""
                update performance p join schedule s using (performance_id)
                set p.max_tickets_per_member = ? where s.schedule_id = ?
                """, limit, schedule);
    }

    private record Outcome(long member, int status, String body) {
        String errorCode() {
            return JsonPath.read(body, "$.errorCode");
        }
    }

    private record StoredSeat(String status, Long member, LocalDateTime until) {
    }
}
