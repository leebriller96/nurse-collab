package com.nursecollab.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nursecollab.domain.encounter.entity.Encounter;
import com.nursecollab.domain.encounter.service.AdmissionService;
import com.nursecollab.domain.patient.entity.Patient;
import com.nursecollab.domain.patient.entity.Sex;
import com.nursecollab.domain.patient.repository.PatientRepository;
import com.nursecollab.domain.staff.repository.StaffRepository;
import com.nursecollab.domain.workorder.service.WorkOrderDelayWatcher;
import com.nursecollab.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 접수 지연 알림.
 *
 * 기다리지 않는다. 요청 시각을 SQL 로 되돌리고 감시를 직접 한 번 돌린다.
 * 배경 스케줄러가 먼저 돌아도 결과가 같게 "몇 번 돌렸나" 가 아니라 "알림이 몇 건인가" 를 본다.
 */
class DelayEscalationApiTest extends IntegrationTest {

    private static final AtomicInteger PATIENT_SEQ = new AtomicInteger(51000);

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper om;
    @Autowired private StaffRepository staffRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private AdmissionService admissionService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private WorkOrderDelayWatcher watcher;

    private UUID subjectRef;
    private long brainMriId;

    @BeforeEach
    void setUp() {
        var ward = staffRepository.findByLoginIdWithDepartment("ward01").orElseThrow().getDepartment();
        Patient patient = patientRepository.save(Patient.create(
                "P%07d".formatted(PATIENT_SEQ.getAndIncrement()), "한OO",
                LocalDate.of(1949, 7, 2), Sex.F, null, null));
        Encounter encounter = admissionService.admit(patient, ward.getId(), "305", "1",
                OffsetDateTime.now().minusDays(2), "뇌경색", false);
        subjectRef = encounter.getSubjectRef();
        brainMriId = jdbcTemplate.queryForObject(
                "select id from service_item where code = 'MRI_BRAIN'", Long.class);
    }

    @Test
    void 응급_요청이_10분째_접수되지_않으면_수간호사와_요청자에게_한_번_알린다() throws Exception {
        long orderId = createRequest("EMERGENCY");
        backdate(orderId, 11);

        watcher.run();
        watcher.run();

        // 3병동 수간호사: 요청을 걸지도 받지도 않았지만 병동을 움직일 수 있는 사람이다
        assertThat(delayedNotificationsFor("head01", orderId)).isEqualTo(1);
        // 새 요청 알림을 놓친 것이 문제다. 요청한 사람은 자기 요청이 멈춰 있는 것을 알아야 한다.
        // 두 번 돌려도 한 건이다 — 매분 울리면 결국 끈다.
        assertThat(delayedNotificationsFor("ward01", orderId)).isEqualTo(1);
    }

    @Test
    void 긴급_요청은_30분까지_기다린다() throws Exception {
        long orderId = createRequest("URGENT");
        backdate(orderId, 11);

        watcher.run();

        assertThat(delayedNotificationsFor("head01", orderId)).isZero();
    }

    @Test
    void 접수된_요청은_늦게_접수됐어도_올리지_않는다() throws Exception {
        long orderId = createRequest("EMERGENCY");
        mvc.perform(post("/api/v1/work-orders/" + orderId + "/transitions")
                        .header("Authorization", bearer("mri01"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"toStatus":"ACCEPTED","scheduledAt":"%s","version":0}"""
                                .formatted(OffsetDateTime.now().plusMinutes(20))))
                .andExpect(status().isOk());
        backdate(orderId, 15);

        watcher.run();

        assertThat(delayedNotificationsFor("head01", orderId)).isZero();
    }

    @Test
    void 지연을_알려도_버전은_그대로라_접수가_튕기지_않는다() throws Exception {
        long orderId = createRequest("EMERGENCY");
        backdate(orderId, 11);

        watcher.run();

        // 화면은 목록에서 받은 버전 0 을 들고 있다. 알림 때문에 버전이 오르면
        // 간호사는 "다른 사용자가 먼저 처리했습니다" 를 받고 응급 요청 접수가 한 번 더 밀린다.
        mvc.perform(post("/api/v1/work-orders/" + orderId + "/transitions")
                        .header("Authorization", bearer("mri01"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"toStatus":"ACCEPTED","scheduledAt":"%s","version":0}"""
                                .formatted(OffsetDateTime.now().plusMinutes(20))))
                .andExpect(status().isOk());
    }

    @Test
    void 반나절_넘게_묵은_요청은_올리지_않는다() throws Exception {
        long orderId = createRequest("EMERGENCY");
        backdate(orderId, 13 * 60);

        watcher.run();

        // 서버가 오래 꺼져 있다 켜지면 묵은 요청이 한꺼번에 울린다. 그만큼 묵은 것은 알림으로 안 풀린다.
        assertThat(delayedNotificationsFor("head01", orderId)).isZero();
    }

    // ── 도우미 ──────────────────────────────────────────────

    private long createRequest(String priority) throws Exception {
        String body = mvc.perform(post("/api/v1/work-orders")
                        .header("Authorization", bearer("ward01"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subjectRef":"%s","serviceItemId":%d,"priority":"%s"}"""
                                .formatted(subjectRef, brainMriId, priority)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return om.readTree(body).get("id").asLong();
    }

    private void backdate(long orderId, int minutes) {
        jdbcTemplate.update("update work_order set requested_at = now() - make_interval(mins => ?) where id = ?",
                minutes, orderId);
    }

    private long delayedNotificationsFor(String loginId, long orderId) throws Exception {
        String body = mvc.perform(get("/api/v1/notifications")
                        .param("size", "100")
                        .header("Authorization", bearer(loginId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long count = 0;
        for (var n : om.readTree(body).get("page").get("content")) {
            if (n.get("notiType").asText().equals("DELAYED") && n.get("refId").asLong() == orderId) {
                count++;
            }
        }
        return count;
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
