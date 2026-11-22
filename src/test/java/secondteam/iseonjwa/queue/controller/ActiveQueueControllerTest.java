package secondteam.iseonjwa.queue.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import secondteam.iseonjwa.member.entity.Member;
import secondteam.iseonjwa.member.repository.MemberRepository;
import secondteam.iseonjwa.performance.entity.Genre;
import secondteam.iseonjwa.performance.entity.Performance;
import secondteam.iseonjwa.performance.entity.PerformanceStatus;
import secondteam.iseonjwa.performance.repository.PerformanceRepository;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:queue-active-test;MODE=MySQL",
        "queue.active.max-size=4",
        "queue.active.ttl-seconds=600"
})
@AutoConfigureMockMvc
@Import(WaitingQueueControllerTest.TestQueueConfig.class)
class ActiveQueueControllerTest {

    private static final String WAITING_ENDPOINT = "/api/queue/waiting";
    private static final String STATUS_ENDPOINT = "/api/queue/status";
    private static final String ACTIVE_ENDPOINT = "/api/queue/active";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PerformanceRepository performanceRepository;

    @Autowired
    private WaitingQueueControllerTest.InMemoryQueueRepository queueRepository;

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
        for (int i = 1; i <= 12; i++) {
            members.add(memberRepository.save(Member.builder()
                    .email("active-member" + i + "@test.com")
                    .password("encoded")
                    .name("회원" + i)
                    .phone("0108888" + String.format("%04d", i))
                    .build()));
        }
    }

    @Test
    void movesMemberFromWaitingToActiveWithTenMinuteTtlAndAdvancesBehindMembers() throws Exception {
        Long firstMemberId = members.get(0).getMemberId();
        Long secondMemberId = members.get(1).getMemberId();

        enqueueWaiting(firstMemberId, performance1.getPerformanceId());
        enqueueWaiting(secondMemberId, performance1.getPerformanceId());

        String responseBody = mockMvc.perform(post(ACTIVE_ENDPOINT)
                        .header("X-Member-Id", firstMemberId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"performanceId": %d}
                                """.formatted(performance1.getPerformanceId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.performanceId").value(performance1.getPerformanceId()))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.admittedAt").isString())
                .andExpect(jsonPath("$.data.expiresAt").isString())
                .andReturn()
                .getResponse()
                .getContentAsString();

        OffsetDateTime admittedAt = OffsetDateTime.parse(
                responseBody.replaceAll(".*\"admittedAt\":\"([^\"]+)\".*", "$1")
        );
        OffsetDateTime expiresAt = OffsetDateTime.parse(
                responseBody.replaceAll(".*\"expiresAt\":\"([^\"]+)\".*", "$1")
        );
        assertThat(Duration.between(admittedAt, expiresAt)).isEqualTo(Duration.ofMinutes(10));
        assertThat(admittedAt.getOffset().getTotalSeconds()).isEqualTo(9 * 3600);
        assertThat(expiresAt.getOffset().getTotalSeconds()).isEqualTo(9 * 3600);

        // 1번 회원의 통합 상태는 ACTIVE(position=0, canEnter=true)로 전환되어야 함
        mockMvc.perform(get(STATUS_ENDPOINT)
                        .header("X-Member-Id", firstMemberId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.position").value(0))
                .andExpect(jsonPath("$.data.canEnter").value(true))
                .andExpect(jsonPath("$.data.expiresAt").value(responseBody.replaceAll(".*\"expiresAt\":\"([^\"]+)\".*", "$1")));

        // 2번 회원은 대기 순번이 2 -> 1로 당겨져야 함
        mockMvc.perform(get(STATUS_ENDPOINT)
                        .header("X-Member-Id", secondMemberId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("WAITING"))
                .andExpect(jsonPath("$.data.position").value(1))
                .andExpect(jsonPath("$.data.canEnter").value(true));
    }

    @Test
    void returnsIdempotent200WithoutExtendingExpiryWhenAlreadyActiveInSamePerformance() throws Exception {
        Long memberId = members.get(0).getMemberId();
        OffsetDateTime fixedExpiresAt = OffsetDateTime.parse("2099-11-22T18:41:00+09:00");
        queueRepository.putActiveForTest(memberId, performance1.getPerformanceId(), fixedExpiresAt.toInstant());

        mockMvc.perform(post(ACTIVE_ENDPOINT)
                        .header("X-Member-Id", memberId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"performanceId": %d}
                                """.formatted(performance1.getPerformanceId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.performanceId").value(performance1.getPerformanceId()))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.admittedAt").value("2099-11-22T18:31:00+09:00"))
                .andExpect(jsonPath("$.data.expiresAt").value("2099-11-22T18:41:00+09:00"));
    }

    @Test
    void rejectsWhenNotInTurnOrCapacityFullAndAdmitsAfterActiveMemberExpires() throws Exception {
        Instant now = Instant.now();
        // 작업열 4석 중 3석은 유효, 1석은 잠시 유효했다가 만료될 엔트리가 아닌 시나리오 검증:
        // 우선 4석 모두 유효하게 채워 만석(max-size=4) 상태로 만듦
        for (int i = 0; i < 4; i++) {
            queueRepository.putActiveForTest(
                    members.get(i).getMemberId(),
                    performance1.getPerformanceId(),
                    now.plusSeconds(600)
            );
        }

        Long waitingFirst = members.get(4).getMemberId();
        Long waitingSecond = members.get(5).getMemberId();
        enqueueWaiting(waitingFirst, performance1.getPerformanceId());
        enqueueWaiting(waitingSecond, performance1.getPerformanceId());

        // 만석이므로 대기 1번도 409 CANNOT_ENTER_ACTIVE_QUEUE
        mockMvc.perform(post(ACTIVE_ENDPOINT)
                        .header("X-Member-Id", waitingFirst)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"performanceId": %d}
                                """.formatted(performance1.getPerformanceId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CANNOT_ENTER_ACTIVE_QUEUE"));

        // 기존 작업열 회원 1명을 만료 상태로 전환 -> 남은 자리 1석 확보
        queueRepository.putActiveForTest(
                members.get(0).getMemberId(),
                performance1.getPerformanceId(),
                now.minusSeconds(1)
        );

        // 남은 자리가 1석이므로 대기 2번은 아직 순서가 아님 -> 409 CANNOT_ENTER_ACTIVE_QUEUE
        mockMvc.perform(post(ACTIVE_ENDPOINT)
                        .header("X-Member-Id", waitingSecond)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"performanceId": %d}
                                """.formatted(performance1.getPerformanceId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CANNOT_ENTER_ACTIVE_QUEUE"));

        // 대기 1번은 빈자리 1석으로 정상 입장 성공 -> 200 OK
        mockMvc.perform(post(ACTIVE_ENDPOINT)
                        .header("X-Member-Id", waitingFirst)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"performanceId": %d}
                                """.formatted(performance1.getPerformanceId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void admitsAtMostMaxActiveSizeUnderConcurrentRequests() throws Exception {
        int candidateCount = 10;
        for (int i = 0; i < candidateCount; i++) {
            enqueueWaiting(members.get(i).getMemberId(), performance1.getPerformanceId());
        }

        CountDownLatch ready = new CountDownLatch(candidateCount);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(candidateCount)) {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < candidateCount; i++) {
                Long memberId = members.get(i).getMemberId();
                Callable<Integer> task = () -> {
                    ready.countDown();
                    start.await();
                    return mockMvc.perform(post(ACTIVE_ENDPOINT)
                                    .header("X-Member-Id", memberId)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("""
                                            {"performanceId": %d}
                                            """.formatted(performance1.getPerformanceId())))
                            .andReturn()
                            .getResponse()
                            .getStatus();
                };
                futures.add(executor.submit(task));
            }

            try {
                assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            } finally {
                start.countDown();
            }

            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : futures) {
                statuses.add(future.get(10, TimeUnit.SECONDS));
            }

            // max-size=4 이므로 정확히 앞순번 4명만 200 OK, 나머지 6명은 409
            assertThat(statuses.stream().filter(s -> s == 200).count()).isEqualTo(4);
            assertThat(statuses.stream().filter(s -> s == 409).count()).isEqualTo(6);
            assertThat(queueRepository.totalWaitingCount()).isEqualTo(6);
        }
    }

    @Test
    void returnsNotFoundOrBadRequestForMissingEntryOrInvalidInput() throws Exception {
        Long unqueuedMemberId = members.get(0).getMemberId();
        Long otherPerfWaitingMemberId = members.get(1).getMemberId();
        Long expiredActiveMemberId = members.get(2).getMemberId();

        enqueueWaiting(otherPerfWaitingMemberId, performance2.getPerformanceId());
        queueRepository.putActiveForTest(
                expiredActiveMemberId,
                performance1.getPerformanceId(),
                Instant.now().minusSeconds(5)
        );

        // 대기열에 없는 회원 -> 404 QUEUE_ENTRY_NOT_FOUND
        mockMvc.perform(post(ACTIVE_ENDPOINT)
                        .header("X-Member-Id", unqueuedMemberId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"performanceId": %d}
                                """.formatted(performance1.getPerformanceId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("QUEUE_ENTRY_NOT_FOUND"));

        // 다른 공연 대기열에 있는 회원이 공연 1 진입 시도 -> 404 QUEUE_ENTRY_NOT_FOUND
        mockMvc.perform(post(ACTIVE_ENDPOINT)
                        .header("X-Member-Id", otherPerfWaitingMemberId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"performanceId": %d}
                                """.formatted(performance1.getPerformanceId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("QUEUE_ENTRY_NOT_FOUND"));

        // 작업열 만료 시각이 지난 회원 -> 404 QUEUE_ENTRY_NOT_FOUND
        mockMvc.perform(post(ACTIVE_ENDPOINT)
                        .header("X-Member-Id", expiredActiveMemberId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"performanceId": %d}
                                """.formatted(performance1.getPerformanceId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("QUEUE_ENTRY_NOT_FOUND"));

        // 존재하지 않는 회원 / 공연 -> 404
        mockMvc.perform(post(ACTIVE_ENDPOINT)
                        .header("X-Member-Id", 999999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"performanceId": %d}
                                """.formatted(performance1.getPerformanceId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("MEMBER_NOT_FOUND"));

        mockMvc.perform(post(ACTIVE_ENDPOINT)
                        .header("X-Member-Id", unqueuedMemberId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"performanceId": 999999}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PERFORMANCE_NOT_FOUND"));

        // 헤더/바디 오류 -> 400 INVALID_REQUEST
        mockMvc.perform(post(ACTIVE_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"performanceId": 1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));

        mockMvc.perform(post(ACTIVE_ENDPOINT)
                        .header("X-Member-Id", unqueuedMemberId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
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
