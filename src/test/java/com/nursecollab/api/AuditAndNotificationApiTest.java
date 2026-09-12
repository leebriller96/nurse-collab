package com.nursecollab.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nursecollab.domain.encounter.entity.Encounter;
import com.nursecollab.domain.encounter.repository.EncounterRepository;
import com.nursecollab.domain.patient.entity.Patient;
import com.nursecollab.domain.patient.entity.Sex;
import com.nursecollab.domain.patient.repository.PatientRepository;
import com.nursecollab.domain.staff.repository.StaffRepository;
import com.nursecollab.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 접근 기록과 알림함.
 *
 * 둘 다 "내 것만 본다" 가 핵심이다.
 * 접근 기록은 관리자만 보고, 알림은 받는 사람 본인만 본다.
 * 이 판정이 서비스와 컨트롤러에 흩어져 있어 HTTP 로 확인해야 확실하다.
 */
class AuditAndNotificationApiTest extends IntegrationTest {

    private static final AtomicInteger PATIENT_SEQ = new AtomicInteger(5000);

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper om;
    @Autowired private StaffRepository staffRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private EncounterRepository encounterRepository;

    private Long encounterId;

    @BeforeEach
    void setUp() {
        var ward = staffRepository.findByLoginIdWithDepartment("ward01").orElseThrow().getDepartment();
        Patient patient = patientRepository.save(Patient.create(
                "P%07d".formatted(PATIENT_SEQ.getAndIncrement()), "정OO",
                LocalDate.of(1965, 1, 30), Sex.M, null, null));
        encounterId = encounterRepository.save(Encounter.admit(patient, ward, "503", "2",
                OffsetDateTime.now().minusDays(1), "당뇨병성 신증", true)).getId();
    }

    // ── 접근 기록 ───────────────────────────────────────────

    @Test
    void 일반_간호사는_접근_기록을_볼_수_없다() throws Exception {
        mvc.perform(get("/api/v1/audit-logs").header("Authorization", bearer("ward01")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERM-003"));
    }

    @Test
    void 수간호사도_접근_기록은_볼_수_없다() throws Exception {
        // 통계는 보지만 누가 무엇을 열어봤는지는 못 본다.
        // 감시 기록을 감시 대상이 볼 수 있으면 기록의 뜻이 없어진다.
        mvc.perform(get("/api/v1/audit-logs").header("Authorization", bearer("head01")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERM-003"));
    }

    @Test
    void 관리자는_접근_기록을_본다() throws Exception {
        mvc.perform(get("/api/v1/audit-logs").header("Authorization", bearer("admin01")))
                .andExpect(status().isOk());
    }

    @Test
    void 기간이_거꾸로면_VAL_001() throws Exception {
        mvc.perform(get("/api/v1/audit-logs")
                        .param("from", "2026-09-10").param("to", "2026-09-01")
                        .header("Authorization", bearer("admin01")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VAL-001"));
    }

    @Test
    void 환자를_열어보면_접근_기록에_남는다() throws Exception {
        // @Audited + AOP 가 자동으로 적재한다. 이게 이 기능의 전부라
        // 조용히 안 쌓이면 아무도 모른다. 실제로 개발 중에 그랬다.
        mvc.perform(get("/api/v1/encounters/" + encounterId)
                        .header("Authorization", bearer("ward01")))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/audit-logs")
                        .param("from", LocalDate.now().toString())
                        .header("Authorization", bearer("admin01")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].action").value("VIEW"));
    }

    // ── 알림함 ──────────────────────────────────────────────

    @Test
    void 알림은_받는_사람에게만_간다() throws Exception {
        // 절대 개수가 아니라 이 요청으로 늘어난 만큼을 본다.
        // 다른 테스트가 같은 계정에 알림을 남길 수 있어서, 0 을 기대하면
        // 이 테스트의 성패가 실행 순서에 달리게 된다.
        long ctBefore = unreadCountOf("ct01");
        long wardBefore = unreadCountOf("ward01");
        long mriBefore = unreadCountOf("mri01");

        // MRI 로 보낸 요청이므로 MRI실만 받아야 한다
        createRequestAndFindNotification();

        assertThat(unreadCountOf("mri01")).isGreaterThan(mriBefore);
        // CT실은 이 요청과 아무 관계가 없다
        assertThat(unreadCountOf("ct01")).isEqualTo(ctBefore);
        // 행위자 본인에게는 자기가 한 일을 알리지 않는다
        assertThat(unreadCountOf("ward01")).isEqualTo(wardBefore);
    }

    @Test
    void 토큰_없이는_알림을_볼_수_없다() throws Exception {
        mvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 남의_알림은_읽음_처리할_수_없다_NTF_000() throws Exception {
        // 요청을 하나 만들면 상대 파트에 알림이 생긴다
        long notificationId = createRequestAndFindNotification();

        // 받는 사람이 아닌 다른 간호사가 손대려 하면 없는 것으로 취급한다.
        // "권한 없음" 대신 "없음" 인 이유는 남의 알림이 존재한다는 사실조차
        // 알려 줄 이유가 없기 때문이다.
        mvc.perform(patch("/api/v1/notifications/" + notificationId + "/read")
                        .header("Authorization", bearer("ct01")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NTF-000"));
    }

    @Test
    void 받는_사람은_읽음_처리할_수_있다() throws Exception {
        long notificationId = createRequestAndFindNotification();

        mvc.perform(patch("/api/v1/notifications/" + notificationId + "/read")
                        .header("Authorization", bearer("mri01")))
                .andExpect(status().isNoContent());
    }

    // ── 도우미 ──────────────────────────────────────────────

    /** MRI실로 요청을 보내 mri01 에게 알림이 쌓이게 하고 그 알림 id 를 준다 */
    private long createRequestAndFindNotification() throws Exception {
        // MRI 검사를 골라야 mri01 이 받는다. 수행 파트는 업무 항목이 정한다.
        var exams = om.readTree(mvc.perform(get("/api/v1/service-items")
                        .header("Authorization", bearer("ward01")))
                .andReturn().getResponse().getContentAsString());
        long serviceItemId = -1;
        for (var e : exams) {
            if (e.get("code").asText().startsWith("MRI")) {
                serviceItemId = e.get("id").asLong();
                break;
            }
        }

        mvc.perform(post("/api/v1/work-orders")
                        .header("Authorization", bearer("ward01"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"encounterId":%d,"serviceItemId":%d,"priority":"ROUTINE"}"""
                                .formatted(encounterId, serviceItemId)))
                .andExpect(status().isCreated());

        String body = mvc.perform(get("/api/v1/notifications")
                        .header("Authorization", bearer("mri01")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        // 목록과 미읽음 수를 함께 주는 구조라 page 아래에 내용이 들어 있다
        return om.readTree(body).get("page").get("content").get(0).get("id").asLong();
    }

    private long unreadCountOf(String loginId) throws Exception {
        String body = mvc.perform(get("/api/v1/notifications")
                        .header("Authorization", bearer(loginId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return om.readTree(body).get("unreadCount").asLong();
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
