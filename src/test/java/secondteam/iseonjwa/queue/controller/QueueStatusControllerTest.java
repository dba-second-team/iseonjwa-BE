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
import secondteam.iseonjwa.member.entity.Member;
import secondteam.iseonjwa.member.repository.MemberRepository;
import secondteam.iseonjwa.performance.entity.Genre;
import secondteam.iseonjwa.performance.entity.Performance;
import secondteam.iseonjwa.performance.entity.PerformanceStatus;
import secondteam.iseonjwa.performance.repository.PerformanceRepository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:queue-status-test;MODE=MySQL",
        "queue.active.max-size=10"
})
@AutoConfigureMockMvc
@Import(WaitingQueueControllerTest.TestQueueConfig.class)
class QueueStatusControllerTest {

    private static final String WAITING_ENDPOINT = "/api/queue/waiting";
    private static final String STATUS_ENDPOINT = "/api/queue/status";

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
        for (int i = 1; i <= 20; i++) {
            members.add(memberRepository.save(Member.builder()
                    .email("status-member" + i + "@test.com")
                    .password("encoded")
                    .name("회원" + i)
                    .phone("0109999" + String.format("%04d", i))
                    .build()));
        }
    }

    @Test
    void returnsWaitingStatusAndCalculatesCanEnterFromRemainingActiveSlots() throws Exception {
        Instant now = Instant.now();
        // 공연 1 작업열에 유효한 회원 8명 배치 -> 남은 자리 2석 (max-size=10)
        for (int i = 0; i < 8; i++) {
            queueRepository.putActiveForTest(
                    members.get(i).getMemberId(),
                    performance1.getPerformanceId(),
                    now.plusSeconds(600)
            );
        }
        // 이미 만료된 작업열 회원 2명도 함께 배치 -> 유효 인원 집계에서 제외되어야 함
        queueRepository.putActiveForTest(
                members.get(8).getMemberId(),
                performance1.getPerformanceId(),
                now.minusSeconds(5)
        );
        queueRepository.putActiveForTest(
                members.get(9).getMemberId(),
                performance1.getPerformanceId(),
                now.minusSeconds(1)
        );

        // 대기열 1, 2, 3번 진입
        for (int i = 10; i < 13; i++) {
            mockMvc.perform(post(WAITING_ENDPOINT)
                            .header("X-Member-Id", members.get(i).getMemberId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"performanceId": %d}
                                    """.formatted(performance1.getPerformanceId())))
                    .andExpect(status().isCreated());
        }

        // 대기 1번, 2번은 남은 자리(2석) 이하이므로 canEnter = true
        mockMvc.perform(get(STATUS_ENDPOINT)
                        .header("X-Member-Id", members.get(10).getMemberId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.performanceId").value(performance1.getPerformanceId()))
                .andExpect(jsonPath("$.data.status").value("WAITING"))
                .andExpect(jsonPath("$.data.position").value(1))
                .andExpect(jsonPath("$.data.canEnter").value(true))
                .andExpect(jsonPath("$.data.expiresAt").value(Matchers.nullValue()));

        mockMvc.perform(get(STATUS_ENDPOINT)
                        .header("X-Member-Id", members.get(11).getMemberId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.performanceId").value(performance1.getPerformanceId()))
                .andExpect(jsonPath("$.data.status").value("WAITING"))
                .andExpect(jsonPath("$.data.position").value(2))
                .andExpect(jsonPath("$.data.canEnter").value(true))
                .andExpect(jsonPath("$.data.expiresAt").value(Matchers.nullValue()));

        // 대기 3번은 남은 자리(2석)를 초과하므로 canEnter = false
        mockMvc.perform(get(STATUS_ENDPOINT)
                        .header("X-Member-Id", members.get(12).getMemberId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.performanceId").value(performance1.getPerformanceId()))
                .andExpect(jsonPath("$.data.status").value("WAITING"))
                .andExpect(jsonPath("$.data.position").value(3))
                .andExpect(jsonPath("$.data.canEnter").value(false))
                .andExpect(jsonPath("$.data.expiresAt").value(Matchers.nullValue()));
    }

    @Test
    void isolatesActiveSlotsPerPerformanceWhenEvaluatingCanEnter() throws Exception {
        Instant now = Instant.now();
        // 공연 1 작업열 10석 만석 세팅
        for (int i = 0; i < 10; i++) {
            queueRepository.putActiveForTest(
                    members.get(i).getMemberId(),
                    performance1.getPerformanceId(),
                    now.plusSeconds(600)
            );
        }

        // 공연 1 대기자 -> 만석이므로 position 1이어도 canEnter = false
        mockMvc.perform(post(WAITING_ENDPOINT)
                        .header("X-Member-Id", members.get(10).getMemberId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"performanceId": %d}
                                """.formatted(performance1.getPerformanceId())))
                .andExpect(status().isCreated());

        // 공연 2 대기자 -> 공연 2 작업열은 비어 있으므로 canEnter = true
        mockMvc.perform(post(WAITING_ENDPOINT)
                        .header("X-Member-Id", members.get(11).getMemberId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"performanceId": %d}
                                """.formatted(performance2.getPerformanceId())))
                .andExpect(status().isCreated());

        mockMvc.perform(get(STATUS_ENDPOINT)
                        .header("X-Member-Id", members.get(10).getMemberId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.performanceId").value(performance1.getPerformanceId()))
                .andExpect(jsonPath("$.data.position").value(1))
                .andExpect(jsonPath("$.data.canEnter").value(false));

        mockMvc.perform(get(STATUS_ENDPOINT)
                        .header("X-Member-Id", members.get(11).getMemberId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.performanceId").value(performance2.getPerformanceId()))
                .andExpect(jsonPath("$.data.position").value(1))
                .andExpect(jsonPath("$.data.canEnter").value(true));
    }

    @Test
    void returnsActiveStatusWithKstOffsetExpiresAt() throws Exception {
        OffsetDateTime targetExpiresAt = OffsetDateTime.parse("2099-11-22T18:40:00+09:00");
        Long memberId = members.get(0).getMemberId();
        queueRepository.putActiveForTest(memberId, performance1.getPerformanceId(), targetExpiresAt.toInstant());

        mockMvc.perform(get(STATUS_ENDPOINT)
                        .header("X-Member-Id", memberId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.performanceId").value(performance1.getPerformanceId()))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.position").value(0))
                .andExpect(jsonPath("$.data.canEnter").value(true))
                .andExpect(jsonPath("$.data.expiresAt").value("2099-11-22T18:40:00+09:00"));
    }

    @Test
    void returnsQueueEntryNotFoundWhenNotInQueueOrActiveSessionExpired() throws Exception {
        Long unqueuedMemberId = members.get(0).getMemberId();
        Long expiredMemberId = members.get(1).getMemberId();
        queueRepository.putActiveForTest(
                expiredMemberId,
                performance1.getPerformanceId(),
                Instant.now().minusSeconds(1)
        );

        // 어디에도 진입하지 않은 회원 -> 404 QUEUE_ENTRY_NOT_FOUND
        mockMvc.perform(get(STATUS_ENDPOINT)
                        .header("X-Member-Id", unqueuedMemberId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("QUEUE_ENTRY_NOT_FOUND"));

        // 작업열 만료 시각이 지난 회원 -> 404 QUEUE_ENTRY_NOT_FOUND
        mockMvc.perform(get(STATUS_ENDPOINT)
                        .header("X-Member-Id", expiredMemberId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("QUEUE_ENTRY_NOT_FOUND"));
    }

    @Test
    void returnsMemberNotFoundOrBadRequestForInvalidHeader() throws Exception {
        mockMvc.perform(get(STATUS_ENDPOINT)
                        .header("X-Member-Id", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("MEMBER_NOT_FOUND"));

        mockMvc.perform(get(STATUS_ENDPOINT))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));

        mockMvc.perform(get(STATUS_ENDPOINT)
                        .header("X-Member-Id", "invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
    }
}
