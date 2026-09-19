package com.nursecollab.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nursecollab.domain.encounter.entity.Encounter;
import com.nursecollab.domain.encounter.service.AdmissionService;
import com.nursecollab.domain.patient.entity.Patient;
import com.nursecollab.domain.patient.entity.Sex;
import com.nursecollab.domain.patient.repository.PatientRepository;
import com.nursecollab.domain.staff.repository.StaffRepository;
import com.nursecollab.domain.workorder.repository.ServiceItemRepository;
import com.nursecollab.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 실시간 채널의 문턱.
 *
 * 파트 채널에는 요청번호·병실·가명·행위자 이름이 실린다. 이걸 받을 수 있는 사람은
 * 그 파트에 로그인한 사람뿐이어야 한다. 한동안 CONNECT 만 검사하고 SUBSCRIBE 는
 * 그냥 통과시켜서, 토큰 없이 붙어 아무 파트 채널이나 받을 수 있었다.
 *
 * MockMvc 로는 잡을 수 없다. STOMP 프레임이 실제 채널 인터셉터를 지나야 하므로
 * 포트를 열고 진짜 WebSocket 으로 붙는다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RealtimeChannelApiTest extends IntegrationTest {

    private static final AtomicInteger PATIENT_SEQ = new AtomicInteger(71000);
    private static final long WAIT_SECONDS = 5;

    @LocalServerPort private int port;
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper om;
    @Autowired private StaffRepository staffRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private AdmissionService admissionService;
    @Autowired private ServiceItemRepository serviceItemRepository;
    @Autowired private SimpUserRegistry userRegistry;

    private final List<StompSession> sessions = new ArrayList<>();
    private WebSocketStompClient client;
    private UUID subjectRef;
    private Long wardId;
    private Long mriId;

    @BeforeEach
    void setUp() {
        // 기본 변환기(SimpleMessageConverter)는 본문을 바이트 그대로 준다.
        // StringMessageConverter 는 text/plain 만 받아서 application/json 방송을 조용히 버린다.
        client = new WebSocketStompClient(new StandardWebSocketClient());

        wardId = departmentOf("ward01");
        mriId = departmentOf("mri01");

        Patient patient = patientRepository.save(Patient.create(
                "P%07d".formatted(PATIENT_SEQ.getAndIncrement()), "서OO",
                LocalDate.of(1970, 8, 14), Sex.M, null, null));
        Encounter encounter = admissionService.admit(patient, wardId, "306", "1",
                OffsetDateTime.now().minusDays(1), "경추 추간판 탈출", true);
        subjectRef = encounter.getSubjectRef();
    }

    @AfterEach
    void tearDown() {
        for (StompSession s : sessions) {
            try {
                s.disconnect();
            } catch (RuntimeException e) {
                // 서버가 이미 끊은 세션
            }
        }
        client.stop();
    }

    // ── 연결 ────────────────────────────────────────────────

    @Test
    void 토큰_없이_붙으면_연결이_거절된다() throws Exception {
        Handler handler = new Handler();
        connect(null, handler);

        assertThat(handler.rejected()).isTrue();
    }

    @Test
    void 만료되거나_위조된_토큰으로는_붙을_수_없다() throws Exception {
        Handler handler = new Handler();
        connect("Bearer not-a-token", handler);

        assertThat(handler.rejected()).isTrue();
    }

    // ── 구독 ────────────────────────────────────────────────

    @Test
    void 다른_파트_채널은_구독할_수_없다() throws Exception {
        Handler handler = new Handler();
        StompSession session = connect(bearer("ward01"), handler);
        session.subscribe("/topic/department/" + mriId, handler);

        // ERROR 프레임이 오고 세션이 닫힌다. 조용히 버리는 게 아니다.
        assertThat(handler.rejected()).isTrue();
    }

    @Test
    void 파트_채널이_아닌_목적지는_없다() throws Exception {
        Handler handler = new Handler();
        StompSession session = connect(bearer("ward01"), handler);
        session.subscribe("/topic/department", handler);

        assertThat(handler.rejected()).isTrue();
    }

    @Test
    void 자기_파트_채널은_받고_요청이_생기면_양쪽_파트에_닿는다() throws Exception {
        Handler ward = new Handler();
        Handler mri = new Handler();
        subscribe(connect(bearer("ward01"), ward), wardId, ward);
        subscribe(connect(bearer("mri01"), mri), mriId, mri);

        String requestNo = createOrder();

        assertThat(ward.next()).contains("ORDER_CREATED").contains(requestNo);
        assertThat(mri.next()).contains("ORDER_CREATED").contains(requestNo);
    }

    @Test
    void 관계없는_파트에는_아무것도_가지_않는다() throws Exception {
        Handler ct = new Handler();
        subscribe(connect(bearer("ct01"), ct), departmentOf("ct01"), ct);

        createOrder();

        assertThat(ct.nextOrNull()).isNull();
    }

    // ── 도우미 ──────────────────────────────────────────────

    private StompSession connect(String authorization, Handler handler) throws Exception {
        StompHeaders headers = new StompHeaders();
        if (authorization != null) {
            headers.add("Authorization", authorization);
        }
        CompletableFuture<StompSession> future =
                client.connectAsync("ws://localhost:" + port + "/ws",
                        (WebSocketHttpHeaders) null, headers, handler);
        try {
            StompSession session = future.get(WAIT_SECONDS, TimeUnit.SECONDS);
            sessions.add(session);
            return session;
        } catch (ExecutionException e) {
            // 거절된 연결. 확인은 handler.rejected() 가 한다.
            handler.closed.complete(null);
            return null;
        }
    }

    /**
     * 구독이 브로커에 등록된 뒤에 요청을 만든다. 기다리지 않으면 방송이 구독보다 먼저 갈 수 있다.
     * 내장 브로커는 SUBSCRIBE 영수증을 주지 않아 서버 쪽 등록부를 직접 본다.
     */
    private void subscribe(StompSession session, Long departmentId, Handler handler) throws Exception {
        String destination = "/topic/department/" + departmentId;
        session.subscribe(destination, handler);

        long deadline = System.currentTimeMillis() + WAIT_SECONDS * 1000;
        while (userRegistry.findSubscriptions(sub -> sub.getDestination().equals(destination)).isEmpty()) {
            assertThat(System.currentTimeMillis()).as("구독이 등록되지 않았다: " + destination).isLessThan(deadline);
            Thread.sleep(20);
        }
    }

    private String createOrder() throws Exception {
        Long brainMri = serviceItemRepository.findAllActiveWithDepartment().stream()
                .filter(e -> e.getCode().equals("MRI_BRAIN"))
                .findFirst().orElseThrow().getId();
        String created = mvc.perform(post("/api/v1/work-orders")
                        .header("Authorization", bearer("ward01"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subjectRef":"%s","serviceItemId":%d,"priority":"ROUTINE"}"""
                                .formatted(subjectRef, brainMri)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return om.readTree(created).get("requestNo").asText();
    }

    private Long departmentOf(String loginId) {
        return staffRepository.findByLoginIdWithDepartment(loginId).orElseThrow()
                .getDepartment().getId();
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

    /**
     * 받은 프레임을 모은다. ERROR 프레임이나 끊김은 "거절" 로 센다.
     * 서버가 거절할 때 ERROR 를 보내고 닫으므로 둘 중 어느 쪽이 먼저 와도 된다.
     */
    private static class Handler extends StompSessionHandlerAdapter implements StompFrameHandler {

        private final LinkedBlockingQueue<String> messages = new LinkedBlockingQueue<>();
        private final CompletableFuture<Void> closed = new CompletableFuture<>();

        @Override
        public Type getPayloadType(StompHeaders headers) {
            return byte[].class;
        }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            // 구독 메시지와 ERROR 프레임이 둘 다 여기로 온다. 구독 콜백은 message-id 가 있다.
            if (headers.getMessageId() != null) {
                messages.add(new String((byte[]) payload, StandardCharsets.UTF_8));
            } else {
                closed.complete(null);
            }
        }

        @Override
        public void handleException(StompSession session, StompCommand command, StompHeaders headers,
                                    byte[] payload, Throwable exception) {
            closed.complete(null);
        }

        @Override
        public void handleTransportError(StompSession session, Throwable exception) {
            closed.complete(null);
        }

        boolean rejected() throws Exception {
            try {
                closed.get(WAIT_SECONDS, TimeUnit.SECONDS);
                return true;
            } catch (java.util.concurrent.TimeoutException e) {
                return false;
            }
        }

        String next() throws InterruptedException {
            String message = messages.poll(WAIT_SECONDS, TimeUnit.SECONDS);
            assertThat(message).as("실시간 메시지가 오지 않았다").isNotNull();
            return message;
        }

        String nextOrNull() throws InterruptedException {
            return messages.poll(2, TimeUnit.SECONDS);
        }
    }
}
