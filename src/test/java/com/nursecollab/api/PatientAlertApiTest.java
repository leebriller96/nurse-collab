package com.nursecollab.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nursecollab.domain.encounter.entity.Encounter;
import com.nursecollab.domain.encounter.service.AdmissionService;
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
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 환자 주의사항.
 *
 * 이 프로젝트에서 가장 특징적인 화면("이 검사 전에 확인이 필요합니다")이
 * 여기 쌓인 것에 기대고 있다. 그동안 시드로만 들어가 있었다.
 */
class PatientAlertApiTest extends IntegrationTest {

    private static final AtomicInteger SEQ = new AtomicInteger(3000);

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper om;
    @Autowired private StaffRepository staffRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private AdmissionService admissionService;

    private Long patientId;
    private Long encounterId;
    private UUID subjectRef;

    @BeforeEach
    void setUp() {
        var ward = staffRepository.findByLoginIdWithDepartment("ward01").orElseThrow().getDepartment();
        Patient patient = patientRepository.save(Patient.create(
                "P%07d".formatted(SEQ.getAndIncrement()), "박OO",
                LocalDate.of(1971, 11, 2), Sex.F, null, null));
        patientId = patient.getId();
        Encounter encounter = admissionService.admit(patient, ward.getId(), "501", "1",
                OffsetDateTime.now().minusDays(3), "요추 추간판탈출증", true);
        encounterId = encounter.getId();
        subjectRef = encounter.getSubjectRef();
    }

    @Test
    void 주의사항을_남기면_환자_화면에서_보인다() throws Exception {
        addAlert("ward01");

        mvc.perform(get("/api/v1/phi/subjects/" + subjectRef + "/alerts")
                        .header("Authorization", bearer("ward01")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].alertType").value("CLAUSTROPHOBIA"));
    }

    @Test
    void 남의_병동_환자에게는_붙일_수_없다_PERM_001() throws Exception {
        // 5병동 간호사는 3병동 환자와 아무 관계가 없다
        mvc.perform(alertRequest("ward02"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERM-001"));
    }

    @Test
    void 요청이_걸린_검사실은_붙일_수_있다() throws Exception {
        // 검사실도 환자를 보는 동안은 관찰한 것을 남길 수 있어야 한다.
        // "검사대에 눕히니 폐소공포 반응" 은 검사실에서만 알 수 있다.
        createMriRequest();

        mvc.perform(alertRequest("mri01")).andExpect(status().isCreated());
    }

    @Test
    void 요청이_없는_검사실은_붙일_수_없다_PERM_001() throws Exception {
        mvc.perform(alertRequest("ct01"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERM-001"));
    }

    @Test
    void 종류가_없으면_VAL_001() throws Exception {
        mvc.perform(post("/api/v1/phi/subjects/" + subjectRef + "/alerts")
                        .header("Authorization", bearer("ward01"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"severity":"WARN","content":"내용만 있다"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VAL-001"));
    }

    @Test
    void 내리면_목록에서_빠지지만_지워지지는_않는다() throws Exception {
        long alertId = addAlert("ward01").get("id").asLong();

        mvc.perform(patch("/api/v1/phi/alerts/" + alertId + "/deactivate")
                        .header("Authorization", bearer("ward01")))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/phi/subjects/" + subjectRef + "/alerts")
                        .header("Authorization", bearer("ward01")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void 없는_주의사항을_내리면_ALT_000() throws Exception {
        mvc.perform(patch("/api/v1/phi/alerts/99999999/deactivate")
                        .header("Authorization", bearer("ward01")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ALT-000"));
    }

    @Test
    void 남긴_주의사항이_검사_전_확인_항목으로_이어진다() throws Exception {
        // 이 연결이 이 프로젝트의 핵심이다. 주의사항 하나가 검사실 화면의 경고가 된다.
        addAlert("ward01");
        createMriRequest();

        // 업무 응답에는 이제 경고가 없다. 진료정보라 원내에서 온다.
        // 확인할 항목이 무엇인지는 업무 쪽(업무 항목)이 알고 있으므로 부르는 쪽이 들고 온다.
        mvc.perform(get("/api/v1/phi/subjects/" + subjectRef + "/checklist")
                        .param("required", "CLAUSTROPHOBIA")
                        .header("Authorization", bearer("mri01")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void 업무_응답에는_환자_정보가_들어_있지_않다() throws Exception {
        // 분리의 핵심이다. 업무 쪽 응답에 이름이나 진단명이 섞이면
        // 그 순간 클라우드 DB 에 진료정보가 쌓이기 시작한다.
        long requestId = createMriRequest();

        mvc.perform(get("/api/v1/work-orders/" + requestId)
                        .header("Authorization", bearer("mri01")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patient").doesNotExist())
                .andExpect(jsonPath("$.alerts").doesNotExist())
                .andExpect(jsonPath("$.checklistWarnings").doesNotExist())
                // 있는 것은 침대와 가명뿐이다
                .andExpect(jsonPath("$.episode.roomNo").value("501"))
                .andExpect(jsonPath("$.episode.subjectRef").value(subjectRef.toString()));
    }

    @Test
    void 관계없는_파트는_가명으로도_사람을_되돌릴_수_없다() throws Exception {
        // 가명을 손에 넣어도 그것만으로는 아무것도 안 된다.
        // 판정은 예전과 같다 — 우리 파트로 온 진행중 요청이 걸려 있는가.
        mvc.perform(get("/api/v1/phi/subjects/" + subjectRef)
                        .header("Authorization", bearer("ct01")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERM-001"));
    }

    @Test
    void 요청이_걸린_파트는_가명으로_사람을_되돌린다() throws Exception {
        addAlert("ward01");
        createMriRequest();

        mvc.perform(get("/api/v1/phi/subjects/" + subjectRef)
                        .header("Authorization", bearer("mri01")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("박OO"))
                .andExpect(jsonPath("$.alerts.length()").value(1));
    }

    // ── 도우미 ──────────────────────────────────────────────

    /** 뇌 MRI 는 확인 항목에 폐소공포가 들어 있다 */
    private long createMriRequest() throws Exception {
        var exams = om.readTree(mvc.perform(get("/api/v1/service-items")
                        .header("Authorization", bearer("ward01")))
                .andReturn().getResponse().getContentAsString());
        long serviceItemId = -1;
        for (var e : exams) {
            if (e.get("code").asText().equals("MRI_BRAIN")) serviceItemId = e.get("id").asLong();
        }

        String body = mvc.perform(post("/api/v1/work-orders")
                        .header("Authorization", bearer("ward01"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subjectRef":"%s","serviceItemId":%d,"priority":"ROUTINE"}"""
                                .formatted(subjectRef, serviceItemId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return om.readTree(body).get("id").asLong();
    }

    private JsonNode addAlert(String actor) throws Exception {
        String body = mvc.perform(alertRequest(actor))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return om.readTree(body);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
            alertRequest(String actor) throws Exception {
        return post("/api/v1/phi/subjects/" + subjectRef + "/alerts")
                .header("Authorization", bearer(actor))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"alertType":"CLAUSTROPHOBIA","severity":"WARN",
                         "content":"이전 MRI 중단 경험 있음"}""");
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
