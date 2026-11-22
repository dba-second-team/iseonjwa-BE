package secondteam.iseonjwa.queue.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import secondteam.iseonjwa.global.error.BusinessException;
import secondteam.iseonjwa.global.error.ErrorCode;
import secondteam.iseonjwa.member.entity.Member;
import secondteam.iseonjwa.member.repository.MemberRepository;
import secondteam.iseonjwa.performance.entity.Genre;
import secondteam.iseonjwa.performance.entity.Performance;
import secondteam.iseonjwa.performance.entity.PerformanceStatus;
import secondteam.iseonjwa.performance.repository.PerformanceRepository;
import secondteam.iseonjwa.queue.dto.QueueStatus;
import secondteam.iseonjwa.queue.repository.QueueRepository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:queue-waiting-test;MODE=MySQL")
@AutoConfigureMockMvc
@Import(WaitingQueueControllerTest.TestQueueConfig.class)
class WaitingQueueControllerTest {

    private static final String ENDPOINT = "/api/queue/waiting";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PerformanceRepository performanceRepository;

    @Autowired
    private InMemoryQueueRepository queueRepository;

    private Performance performance1;
    private Performance performance2;
    private List<Member> members;

    @BeforeEach
    void setUp() {
        queueRepository.clear();
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
        for (int i = 1; i <= 15; i++) {
            members.add(memberRepository.save(Member.builder()
                    .email("member" + i + "@test.com")
                    .password("encoded")
                    .name("회원" + i)
                    .phone("0101234" + String.format("%04d", i))
                    .build()));
        }
    }

    @Test
    void registersWaitingQueueInEntryOrderAndReturnsPosition15OnFifteenthMember() throws Exception {
        for (int i = 0; i < 14; i++) {
            mockMvc.perform(post(ENDPOINT)
                            .header("X-Member-Id", members.get(i).getMemberId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"performanceId": %d}
                                    """.formatted(performance1.getPerformanceId())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.position").value(i + 1));
        }

        OffsetDateTime before = OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        String responseBody = mockMvc.perform(post(ENDPOINT)
                        .header("X-Member-Id", members.get(14).getMemberId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"performanceId": %d}
                                """.formatted(performance1.getPerformanceId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.performanceId").value(performance1.getPerformanceId()))
                .andExpect(jsonPath("$.data.status").value("WAITING"))
                .andExpect(jsonPath("$.data.position").value(15))
                .andExpect(jsonPath("$.data.enteredAt").isString())
                .andReturn()
                .getResponse()
                .getContentAsString();

        OffsetDateTime after = OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        String enteredAtRaw = responseBody.replaceAll(".*\"enteredAt\":\"([^\"]+)\".*", "$1");
        OffsetDateTime enteredAt = OffsetDateTime.parse(enteredAtRaw);
        assertThat(enteredAt).isBetween(before, after);
    }

    @Test
    void calculatesPositionPerPerformanceIndependently() throws Exception {
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post(ENDPOINT)
                            .header("X-Member-Id", members.get(i).getMemberId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"performanceId": %d}
                                    """.formatted(performance1.getPerformanceId())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.position").value(i + 1));
        }

        mockMvc.perform(post(ENDPOINT)
                        .header("X-Member-Id", members.get(2).getMemberId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"performanceId": %d}
                                """.formatted(performance2.getPerformanceId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.performanceId").value(performance2.getPerformanceId()))
                .andExpect(jsonPath("$.data.position").value(1));
    }

    @Test
    void rejectsDuplicateEntryIntoSameOrDifferentPerformanceWaitingQueue() throws Exception {
        Long memberId = members.get(0).getMemberId();

        mockMvc.perform(post(ENDPOINT)
                        .header("X-Member-Id", memberId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"performanceId": %d}
                                """.formatted(performance1.getPerformanceId())))
                .andExpect(status().isCreated());

        // 동일 공연 재진입 차단 (409 ALREADY_IN_QUEUE)
        mockMvc.perform(post(ENDPOINT)
                        .header("X-Member-Id", memberId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"performanceId": %d}
                                """.formatted(performance1.getPerformanceId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ALREADY_IN_QUEUE"));

        // 타 공연 동시 진입 차단 (409 ALREADY_IN_QUEUE)
        mockMvc.perform(post(ENDPOINT)
                        .header("X-Member-Id", memberId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"performanceId": %d}
                                """.formatted(performance2.getPerformanceId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ALREADY_IN_QUEUE"));
    }

    @Test
    void rejectsEntryWhenMemberIsInValidActiveQueueAndAllowsReentryAfterExpiry() throws Exception {
        Long activeMemberId = members.get(0).getMemberId();
        Long expiredMemberId = members.get(1).getMemberId();

        Instant now = Instant.now();
        // 만료 전 active_queue 상태 세팅 (현재 시각 + 10분)
        queueRepository.putActiveForTest(activeMemberId, performance1.getPerformanceId(), now.plusSeconds(600));
        // 이미 만료된 active_queue 상태 세팅 (현재 시각 - 10초)
        queueRepository.putActiveForTest(expiredMemberId, performance1.getPerformanceId(), now.minusSeconds(10));

        // 아직 유효한 작업열 회원은 같은 공연·다른 공연 모두 진입 차단
        mockMvc.perform(post(ENDPOINT)
                        .header("X-Member-Id", activeMemberId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"performanceId": %d}
                                """.formatted(performance2.getPerformanceId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ALREADY_IN_QUEUE"));

        // 작업열 만료 시각이 지난 회원은 정상 재진입 허용
        mockMvc.perform(post(ENDPOINT)
                        .header("X-Member-Id", expiredMemberId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"performanceId": %d}
                                """.formatted(performance2.getPerformanceId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("WAITING"))
                .andExpect(jsonPath("$.data.position").value(1));
    }

    @Test
    void blocksConcurrentDoubleClickAndMultiTabCrossPerformanceRequests() throws Exception {
        Long memberId = members.get(0).getMemberId();
        int threadCount = 10;
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(threadCount)) {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                Long targetPerfId = (i % 2 == 0)
                        ? performance1.getPerformanceId()
                        : performance2.getPerformanceId();
                Callable<Integer> task = () -> {
                    ready.countDown();
                    start.await();
                    return mockMvc.perform(post(ENDPOINT)
                                    .header("X-Member-Id", memberId)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("""
                                            {"performanceId": %d}
                                            """.formatted(targetPerfId)))
                            .andReturn()
                            .getResponse()
                            .getStatus();
                };
                futures.add(executor.submit(task));
            }

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : futures) {
                statuses.add(future.get(10, TimeUnit.SECONDS));
            }

            assertThat(statuses.stream().filter(s -> s == 201).count()).isEqualTo(1);
            assertThat(statuses.stream().filter(s -> s == 409).count()).isEqualTo(threadCount - 1);
            assertThat(queueRepository.totalWaitingCount()).isEqualTo(1);
        }
    }

    @Test
    void returnsNotFoundForMissingMemberOrPerformanceWithoutPollutingQueue() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .header("X-Member-Id", 999999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"performanceId": %d}
                                """.formatted(performance1.getPerformanceId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("MEMBER_NOT_FOUND"));

        mockMvc.perform(post(ENDPOINT)
                        .header("X-Member-Id", members.get(0).getMemberId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"performanceId": 999999}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PERFORMANCE_NOT_FOUND"));

        assertThat(queueRepository.totalWaitingCount()).isZero();
    }

    @Test
    void returnsBadRequestForMissingHeaderOrInvalidBody() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"performanceId": 1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));

        mockMvc.perform(post(ENDPOINT)
                        .header("X-Member-Id", "not-a-number")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"performanceId": 1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));

        mockMvc.perform(post(ENDPOINT)
                        .header("X-Member-Id", members.get(0).getMemberId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));

        mockMvc.perform(post(ENDPOINT)
                        .header("X-Member-Id", members.get(0).getMemberId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestQueueConfig {

        @Bean
        @Primary
        InMemoryQueueRepository inMemoryQueueRepository() {
            return new InMemoryQueueRepository();
        }
    }

    /**
     * Redis Lua 스크립트와 동일한 원자적 의미론(전역 회원 상태 + 공연별 waiting/active ZSet)을 검증하는 인메모리 구현체.
     */
    static class InMemoryQueueRepository implements QueueRepository {

        private record MemberState(long performanceId, QueueStatus status, long expiresAtMillis) {
        }

        private final Map<Long, MemberState> memberStates = new HashMap<>();
        private final Map<Long, LinkedHashSet<Long>> waitingQueues = new HashMap<>();
        private final Map<Long, Map<Long, Long>> activeQueues = new HashMap<>();

        @Override
        public synchronized long enqueueWaiting(long memberId, long performanceId, Instant now) {
            long nowMillis = now.toEpochMilli();
            MemberState existing = memberStates.get(memberId);

            if (existing != null) {
                if (existing.status() == QueueStatus.WAITING) {
                    LinkedHashSet<Long> prevWaiting = waitingQueues.get(existing.performanceId());
                    if (prevWaiting != null && prevWaiting.contains(memberId)) {
                        throw new BusinessException(ErrorCode.ALREADY_IN_QUEUE);
                    }
                    memberStates.remove(memberId);
                } else if (existing.status() == QueueStatus.ACTIVE) {
                    Map<Long, Long> prevActive = activeQueues.get(existing.performanceId());
                    Long exp = prevActive != null ? prevActive.get(memberId) : null;
                    if (exp != null && exp > nowMillis) {
                        throw new BusinessException(ErrorCode.ALREADY_IN_QUEUE);
                    }
                    if (prevActive != null) {
                        prevActive.remove(memberId);
                    }
                    memberStates.remove(memberId);
                }
            }

            LinkedHashSet<Long> waiting = waitingQueues.computeIfAbsent(performanceId, id -> new LinkedHashSet<>());
            waiting.add(memberId);
            memberStates.put(memberId, new MemberState(performanceId, QueueStatus.WAITING, 0L));

            long position = 1L;
            for (Long id : waiting) {
                if (id.equals(memberId)) {
                    return position;
                }
                position++;
            }
            throw new IllegalStateException("대기열 순번 계산 실패");
        }

        synchronized void putActiveForTest(long memberId, long performanceId, Instant expiresAt) {
            long expMillis = expiresAt.toEpochMilli();
            activeQueues.computeIfAbsent(performanceId, id -> new HashMap<>()).put(memberId, expMillis);
            memberStates.put(memberId, new MemberState(performanceId, QueueStatus.ACTIVE, expMillis));
        }

        synchronized int totalWaitingCount() {
            return waitingQueues.values().stream().mapToInt(LinkedHashSet::size).sum();
        }

        synchronized void clear() {
            memberStates.clear();
            waitingQueues.clear();
            activeQueues.clear();
        }
    }
}
