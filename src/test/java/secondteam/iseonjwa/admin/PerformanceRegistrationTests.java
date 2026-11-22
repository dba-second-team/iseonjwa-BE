package secondteam.iseonjwa.admin;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:performance-registration;MODE=MySQL",
        "app.s3.bucket=test-posters"
})
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PerformanceRegistrationTests {
    private static final String ENDPOINT = "/api/admin/performances";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private S3Client s3Client;

    @MockitoBean
    private S3Presigner s3Presigner;

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
    void rejectsInvalidPosterBytesBeforeUploading() throws Exception {
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
        verifyNoInteractions(s3Client);
    }

    @Test
    void uploadsPosterStoresItsKeyAndReturnsThePresignedUrlInTheList() throws Exception {
        var signed = mock(PresignedGetObjectRequest.class);
        String url = "https://test-posters.s3.ap-northeast-2.amazonaws.com/posters/test.png?signature=example";
        when(signed.url()).thenReturn(URI.create(url).toURL());
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(signed);
        byte[] bytes = png();
        var poster = new MockMultipartFile("poster", "client-filename.jpg", "image/jpeg", bytes);

        registerWithPoster("포스터 저장 공연장", poster)
                .andExpect(status().isCreated());

        String key = jdbc.queryForObject("SELECT poster_key FROM performance WHERE venue_name = ?",
                String.class, "포스터 저장 공연장");
        assertThat(key).matches("posters/[0-9a-f-]{36}\\.png");

        var upload = ArgumentCaptor.forClass(PutObjectRequest.class);
        var body = ArgumentCaptor.forClass(RequestBody.class);
        verify(s3Client).putObject(upload.capture(), body.capture());
        assertThat(upload.getValue().bucket()).isEqualTo("test-posters");
        assertThat(upload.getValue().key()).isEqualTo(key);
        assertThat(upload.getValue().contentType()).isEqualTo("image/png");
        try (var uploaded = body.getValue().contentStreamProvider().newStream()) {
            assertThat(uploaded.readAllBytes()).isEqualTo(bytes);
        }

        mockMvc.perform(get(ENDPOINT).header("X-Member-Id", adminId).param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].posterUrl").value(url));

        var read = ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(s3Presigner).presignGetObject(read.capture());
        assertThat(read.getValue().signatureDuration()).isEqualTo(Duration.ofMinutes(15));
        assertThat(read.getValue().getObjectRequest().key()).isEqualTo(key);
    }

    @Test
    void removesTheUploadedPosterWhenRegistrationRollsBack() throws Exception {
        String venue = "포스터 롤백 공연장";
        register(adminId, request(venue, """
                {"startAt":"2026-12-13T19:30:00+09:00"}
                """))
                .andExpect(status().isCreated());

        registerWithPoster(venue, new MockMultipartFile("poster", "poster.png", "image/png", png()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("SCHEDULE_CONFLICT"));

        var upload = ArgumentCaptor.forClass(PutObjectRequest.class);
        var deletion = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).putObject(upload.capture(), any(RequestBody.class));
        verify(s3Client).deleteObject(deletion.capture());
        assertThat(deletion.getValue().key()).isEqualTo(upload.getValue().key());
        assertThat(deletion.getValue().bucket()).isEqualTo(upload.getValue().bucket());
        assertThat(performanceCount(venue)).isEqualTo(1);
    }

    @Test
    void doesNotRegisterThePerformanceWhenS3RejectsTheUpload() throws Exception {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(S3Exception.builder().statusCode(403).build());

        registerWithPoster("업로드 실패 공연장", new MockMultipartFile("poster", "poster.png", "image/png", png()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("POSTER_STORAGE_UNAVAILABLE"));

        assertThat(performanceCount("업로드 실패 공연장")).isZero();
    }

    @Test
    void rejectsPostersLargerThanFiveMegabytes() throws Exception {
        byte[] oversized = new byte[5 * 1024 * 1024 + 1];
        registerWithPoster("큰 포스터 공연장",
                new MockMultipartFile("poster", "poster.png", "image/png", oversized))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.errorCode").value("POSTER_TOO_LARGE"));

        assertThat(performanceCount("큰 포스터 공연장")).isZero();
        verifyNoInteractions(s3Client);
    }

    private ResultActions registerWithPoster(String venue, MockMultipartFile poster) throws Exception {
        return mockMvc.perform(multipart(ENDPOINT)
                .file(performancePart(request(venue, """
                        {"startAt":"2026-12-13T19:30:00+09:00"}
                        """)))
                .file(poster)
                .header("X-Member-Id", adminId));
    }

    private byte[] png() throws IOException {
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", bytes);
        return bytes.toByteArray();
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
