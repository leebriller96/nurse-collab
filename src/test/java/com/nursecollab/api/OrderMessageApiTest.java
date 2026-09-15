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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 요청 대화가 누가·언제(업무 쪽)와 내용(원내)으로 갈려 있는지 확인한다.
 *
 * 한 서버로 띄워도 경로와 응답 모양은 갈라 띄울 때와 같다. 업무 응답에 내용이
 * 한 번이라도 실리면 클라우드 DB 와 로그에 환자 상태가 남는다.
 */
class OrderMessageApiTest extends IntegrationTest {

    private static final AtomicInteger PATIENT_SEQ = new AtomicInteger(41000);
    private static final String CONTENT = "환자분 열이 38.5도라 검사 미뤄주세요";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper om;
    @Autowired private StaffRepository staffRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private AdmissionService admissionService;
    @Autowired private ServiceItemRepository serviceItemRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private long orderId;

    @BeforeEach
    void setUp() throws Exception {
        var ward = staffRepository.findByLoginIdWithDepartment("ward01").orElseThrow().getDepartment();
        Patient patient = patientRepository.save(Patient.create(
                "P%07d".formatted(PATIENT_SEQ.getAndIncrement()), "한OO",
                LocalDate.of(1970, 2, 14), Sex.M, null, null));
        Encounter encounter = admissionService.admit(patient, ward.getId(), "306", "2",
                OffsetDateTime.now().minusDays(1), "폐렴", true);

        Long brainMri = serviceItemRepository.findAllActiveWithDepartment().stream()
                .filter(e -> e.getCode().equals("MRI_BRAIN"))
                .findFirst().orElseThrow().getId();
        String created = mvc.perform(post("/api/v1/work-orders")
                        .header("Authorization", bearer("ward01"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subjectRef":"%s","serviceItemId":%d,"priority":"ROUTINE"}"""
                                .formatted(encounter.getSubjectRef(), brainMri)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        orderId = om.readTree(created).get("id").asLong();
    }

    @Test
    void 대화는_원내로_보내고_상대_파트가_원내에서_읽는다() throws Exception {
        String messageRef = send("ward01", CONTENT);

        // 업무 쪽에는 누가·언제와 열쇠만 있다
        mvc.perform(get("/api/v1/work-orders/" + orderId + "/messages")
                        .header("Authorization", bearer("mri01")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].messageRef").value(messageRef))
                .andExpect(jsonPath("$[0].sender.name").value("김간호"))
                .andExpect(jsonPath("$[0].content").doesNotExist());

        // 내용은 원내에서 받는다
        mvc.perform(get("/api/v1/phi/work-orders/" + orderId + "/messages/bodies")
                        .header("Authorization", bearer("mri01")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].messageRef").value(messageRef))
                .andExpect(jsonPath("$[0].content").value(CONTENT));
    }

    @Test
    void 업무_쪽_응답과_테이블_어디에도_내용이_없다() throws Exception {
        send("ward01", CONTENT);

        String body = mvc.perform(get("/api/v1/work-orders/" + orderId + "/messages")
                        .header("Authorization", bearer("ward01")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("38.5");

        // 칸 자체가 없어야 한다. 있으면 언젠가 누가 채운다.
        Integer contentColumns = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.columns
                where table_name = 'request_message' and column_name = 'content'
                """, Integer.class);
        assertThat(contentColumns).isZero();
    }

    @Test
    void 관여하지_않는_파트는_대화를_남기지_못하고_원내에도_남지_않는다() throws Exception {
        mvc.perform(post("/api/v1/phi/work-orders/" + orderId + "/messages")
                        .header("Authorization", bearer("ct01"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"관계없는 파트가 남겨 본다\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERM-001"));

        // 원내가 먼저 저장하고 업무 쪽이 거절했다. 저장도 되돌려져야 한다.
        Integer bodies = jdbcTemplate.queryForObject(
                "select count(*) from request_message_body where order_id = ?", Integer.class, orderId);
        assertThat(bodies).isZero();
    }

    @Test
    void 관여하지_않는_파트는_원내에서_내용을_읽지_못한다() throws Exception {
        send("ward01", CONTENT);

        mvc.perform(get("/api/v1/phi/work-orders/" + orderId + "/messages/bodies")
                        .header("Authorization", bearer("ct01")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERM-001"));
    }

    @Test
    void 업무_쪽에는_대화를_직접_보내는_길이_없다() throws Exception {
        // 예전 경로가 남아 있으면 화면 하나만 고치지 않아도 내용이 업무 쪽으로 간다
        mvc.perform(post("/api/v1/work-orders/" + orderId + "/messages")
                        .header("Authorization", bearer("ward01"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"예전 길로 보내 본다\"}"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void 내용이_비면_남길_수_없다() throws Exception {
        mvc.perform(post("/api/v1/phi/work-orders/" + orderId + "/messages")
                        .header("Authorization", bearer("ward01"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VAL-001"));
    }

    // ── 도우미 ──────────────────────────────────────────────

    private String send(String loginId, String content) throws Exception {
        String body = mvc.perform(post("/api/v1/phi/work-orders/" + orderId + "/messages")
                        .header("Authorization", bearer(loginId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(java.util.Map.of("content", content))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return om.readTree(body).get("messageRef").asText();
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
