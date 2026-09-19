package com.nursecollab.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nursecollab.domain.encounter.entity.Encounter;
import com.nursecollab.domain.encounter.service.AdmissionService;
import com.nursecollab.domain.notification.push.PushTestDevice;
import com.nursecollab.domain.patient.entity.Patient;
import com.nursecollab.domain.patient.entity.Sex;
import com.nursecollab.domain.patient.repository.PatientRepository;
import com.nursecollab.domain.staff.repository.StaffRepository;
import com.nursecollab.support.IntegrationTest;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.net.InetSocketAddress;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 폰 알림(Web Push).
 *
 * 구글·애플의 푸시 서비스 자리에 이 테스트가 작은 HTTP 서버를 띄운다. 서버가 보낸 본문을
 * 기기 개인키로 풀어 알림함과 같은 문구가 들어 있는지까지 본다. "보냈다" 만 확인하면
 * 암호화가 틀려 폰에서 조용히 버려지는 것을 모른다.
 */
class PushApiTest extends IntegrationTest {

    private static final AtomicInteger PATIENT_SEQ = new AtomicInteger(61000);

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper om;
    @Autowired private StaffRepository staffRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private AdmissionService admissionService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private HttpServer pushService;
    private final BlockingQueue<Received> received = new LinkedBlockingQueue<>();
    /** 경로마다 돌려줄 상태 코드. 없으면 201 */
    private final Map<String, Integer> replies = new java.util.concurrent.ConcurrentHashMap<>();
    private UUID subjectRef;
    private long brainMriId;

    record Received(String path, Map<String, String> headers, byte[] body) {}

    @BeforeEach
    void setUp() throws Exception {
        pushService = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        pushService.createContext("/", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            Map<String, String> headers = new java.util.HashMap<>();
            exchange.getRequestHeaders().forEach((k, v) -> headers.put(k.toLowerCase(), v.get(0)));
            String path = exchange.getRequestURI().getPath();
            received.add(new Received(path, headers, body));
            exchange.sendResponseHeaders(replies.getOrDefault(path, 201), -1);
            exchange.close();
        });
        pushService.start();

        var ward = staffRepository.findByLoginIdWithDepartment("ward01").orElseThrow().getDepartment();
        Patient patient = patientRepository.save(Patient.create(
                "P%07d".formatted(PATIENT_SEQ.getAndIncrement()), "서OO",
                LocalDate.of(1955, 4, 9), Sex.M, null, null));
        Encounter encounter = admissionService.admit(patient, ward.getId(), "307", "2",
                OffsetDateTime.now().minusDays(1), "뇌경색", false);
        subjectRef = encounter.getSubjectRef();
        brainMriId = jdbcTemplate.queryForObject(
                "select id from service_item where code = 'MRI_BRAIN'", Long.class);
    }

    @AfterEach
    void tearDown() {
        pushService.stop(0);
    }

    @Test
    void 구독할_때_쓸_공개키를_준다() throws Exception {
        String body = mvc.perform(get("/api/v1/push/public-key").header("Authorization", bearer("ward01")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        // 브라우저 pushManager.subscribe 는 날 점(0x04 로 시작하는 65바이트)을 받는다
        byte[] key = Base64.getUrlDecoder().decode(om.readTree(body).get("publicKey").asText());
        assertThat(key).hasSize(65);
        assertThat(key[0]).isEqualTo((byte) 0x04);
    }

    @Test
    void 알려지지_않은_주소로는_구독할_수_없다_PSH_001() throws Exception {
        // 구독 주소는 브라우저가 보낸 값이다. 믿으면 서버가 아무 주소로나 요청을 쏘게 된다.
        PushTestDevice device = PushTestDevice.create();
        mvc.perform(subscribe("ward01", "https://intranet.example.com/admin/delete-all", device))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PSH-001"));
    }

    @Test
    void 알림이_생기면_구독한_기기로_암호화해_보낸다() throws Exception {
        PushTestDevice device = PushTestDevice.create();
        String endpoint = endpoint("/mri01-" + UUID.randomUUID());
        mvc.perform(subscribe("mri01", endpoint, device)).andExpect(status().isNoContent());

        long orderId = createRequest();

        Received push = awaitPush(endpoint);
        assertThat(push.headers().get("content-encoding")).isEqualTo("aes128gcm");
        assertThat(push.headers().get("authorization")).startsWith("vapid t=").contains(", k=");
        assertThat(push.headers()).containsKey("ttl");

        // VAPID 토큰의 aud 는 배열이 아니라 문자열이어야 한다. 배열로 보냈더니 FCM 이 403 으로 튕겼다 —
        // 이 가짜 서비스는 서명을 따지지 않아 그대로 받아 줬다. 모양을 여기서 못 박는다.
        String jwt = push.headers().get("authorization").replaceFirst("^vapid t=", "").split(",")[0];
        JsonNode claims = om.readTree(Base64.getUrlDecoder().decode(jwt.split("\\.")[1]));
        assertThat(claims.get("aud").isTextual()).isTrue();
        assertThat(claims.get("aud").asText()).isEqualTo("http://localhost:" + pushService.getAddress().getPort());
        assertThat(claims.get("sub").asText()).startsWith("mailto:");

        JsonNode payload = om.readTree(device.decrypt(push.body()));
        // 알림함과 같은 문구다. 이름은 없고 병실과 업무명뿐이다.
        assertThat(payload.get("title").asText()).isEqualTo("3병동에서 새 요청을 보냈습니다");
        assertThat(payload.get("body").asText()).startsWith("307호 / 뇌 MRI");
        assertThat(payload.toString()).doesNotContain("서OO");
        // 같은 요청의 다음 알림이 앞의 것을 덮는다. 한 건에 알림이 여섯 개 쌓이면 폰 알림창이 요청 목록이 된다.
        assertThat(payload.get("tag").asText()).isEqualTo("order-" + orderId);
        assertThat(payload.get("url").asText()).isEqualTo("/orders/" + orderId);
    }

    @Test
    void 요청한_본인의_기기로는_보내지_않는다() throws Exception {
        PushTestDevice device = PushTestDevice.create();
        String endpoint = endpoint("/ward01-" + UUID.randomUUID());
        mvc.perform(subscribe("ward01", endpoint, device)).andExpect(status().isNoContent());

        createRequest();

        // 알림함 규칙과 같다. 방금 누른 사람에게 되돌려 울리지 않는다.
        assertThat(received.poll(2, TimeUnit.SECONDS)).isNull();
    }

    @Test
    void 푸시_서비스가_410이면_구독을_지운다() throws Exception {
        PushTestDevice device = PushTestDevice.create();
        String path = "/gone-" + UUID.randomUUID();
        replies.put(path, 410);
        mvc.perform(subscribe("mri01", endpoint(path), device)).andExpect(status().isNoContent());

        createRequest();
        awaitPush(endpoint(path));

        // 앱을 지웠거나 권한을 거둔 기기다. 남겨 두면 알림마다 헛걸음을 한다.
        long left = 0;
        for (int i = 0; i < 50; i++) {
            left = countSubscriptions(endpoint(path));
            if (left == 0) break;
            Thread.sleep(100);
        }
        assertThat(left).isZero();
    }

    @Test
    void 같은_기기로_다른_사람이_등록하면_넘겨받는다() throws Exception {
        // 병동 폰은 돌려 쓴다. 앞사람의 등록이 남아 있으면 다음 사람이 앞사람 환자 알림을 받는다.
        PushTestDevice device = PushTestDevice.create();
        String endpoint = endpoint("/shared-" + UUID.randomUUID());
        mvc.perform(subscribe("ward01", endpoint, device)).andExpect(status().isNoContent());
        mvc.perform(subscribe("ward02", endpoint, device)).andExpect(status().isNoContent());

        Long owner = jdbcTemplate.queryForObject(
                "select staff_id from push_subscription where endpoint = ?", Long.class, endpoint);
        assertThat(owner).isEqualTo(staffRepository.findByLoginIdWithDepartment("ward02").orElseThrow().getId());
        assertThat(countSubscriptions(endpoint)).isEqualTo(1);
    }

    @Test
    void 로그아웃할_때_이_기기를_빼면_남의_구독은_건드리지_않는다() throws Exception {
        PushTestDevice device = PushTestDevice.create();
        String mine = endpoint("/mine-" + UUID.randomUUID());
        String others = endpoint("/others-" + UUID.randomUUID());
        mvc.perform(subscribe("ward01", mine, device)).andExpect(status().isNoContent());
        mvc.perform(subscribe("ward02", others, device)).andExpect(status().isNoContent());

        mvc.perform(unsubscribe("ward01", mine)).andExpect(status().isNoContent());
        // 남의 기기 주소를 알아도 뺄 수 없다
        mvc.perform(unsubscribe("ward01", others)).andExpect(status().isNoContent());

        assertThat(countSubscriptions(mine)).isZero();
        assertThat(countSubscriptions(others)).isEqualTo(1);
    }

    // ── 도우미 ──────────────────────────────────────────────

    private String endpoint(String path) {
        return "http://localhost:" + pushService.getAddress().getPort() + path;
    }

    private Received awaitPush(String endpoint) throws InterruptedException {
        String path = java.net.URI.create(endpoint).getPath();
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            Received r = received.poll(200, TimeUnit.MILLISECONDS);
            if (r != null && r.path().equals(path)) return r;
        }
        throw new AssertionError("푸시가 오지 않았다: " + endpoint);
    }

    private long countSubscriptions(String endpoint) {
        return jdbcTemplate.queryForObject(
                "select count(*) from push_subscription where endpoint = ?", Long.class, endpoint);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder subscribe(
            String loginId, String endpoint, PushTestDevice device) throws Exception {
        return post("/api/v1/push/subscriptions")
                .header("Authorization", bearer(loginId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"endpoint":"%s","keys":{"p256dh":"%s","auth":"%s"}}"""
                        .formatted(endpoint, device.p256dh(), device.auth()));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder unsubscribe(
            String loginId, String endpoint) throws Exception {
        return delete("/api/v1/push/subscriptions")
                .header("Authorization", bearer(loginId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"endpoint":"%s"}""".formatted(endpoint));
    }

    private long createRequest() throws Exception {
        String body = mvc.perform(post("/api/v1/work-orders")
                        .header("Authorization", bearer("ward01"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subjectRef":"%s","serviceItemId":%d,"priority":"ROUTINE"}"""
                                .formatted(subjectRef, brainMriId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return om.readTree(body).get("id").asLong();
    }

    private String bearer(String loginId) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId":"%s","password":"nurse1234!"}""".formatted(loginId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + om.readTree(body).get("accessToken").asText();
    }
}
