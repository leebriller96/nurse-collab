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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 간호기록의 규칙을 HTTP 로 확인한다.
 *
 * 이 도메인의 규칙이 프로젝트에서 제일 까다롭다.
 * 지우지 못하고, 본인만 고칠 수 있고, 그것도 24시간 안에만 된다.
 * 기록을 나중에 손댈 수 있으면 그 기록으로 아무것도 증명할 수 없기 때문이다.
 */
class NursingRecordApiTest extends IntegrationTest {

    private static final AtomicInteger PATIENT_SEQ = new AtomicInteger(7000);

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper om;
    @Autowired private StaffRepository staffRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private AdmissionService admissionService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private UUID subjectRef;

    @BeforeEach
    void setUp() {
        var ward = staffRepository.findByLoginIdWithDepartment("ward01").orElseThrow().getDepartment();
        Patient patient = patientRepository.save(Patient.create(
                "P%07d".formatted(PATIENT_SEQ.getAndIncrement()), "이OO",
                LocalDate.of(1952, 7, 24), Sex.F, null, null));
        Encounter encounter = admissionService.admit(patient, ward.getId(), "302", "2",
                OffsetDateTime.now().minusDays(2), "폐렴", true);
        subjectRef = encounter.getSubjectRef();
    }

    @Test
    void 인수인계_기록을_남기고_다시_읽는다() throws Exception {
        writeNote("ward01");

        mvc.perform(get("/api/v1/phi/subjects/" + subjectRef + "/nursing-notes")
                        .header("Authorization", bearer("ward01")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].situation").value("오전 회진 후 발열"));
    }

    @Test
    void 본인이_쓴_기록은_고칠_수_있다() throws Exception {
        long noteId = writeNote("ward01").get("id").asLong();

        mvc.perform(editNote(noteId, "ward01", "체온 38.9도로 상승"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situation").value("체온 38.9도로 상승"));
    }

    @Test
    void 기록을_고치면_고치기_전_내용이_원내_기록에_남는다() throws Exception {
        // 수정 전 내용은 그 자체가 진료정보다. 업무 쪽 감사 로그가 아니라 원내에 남아야 한다.
        String created = mvc.perform(post("/api/v1/phi/subjects/" + subjectRef + "/nursing-notes")
                        .header("Authorization", bearer("ward01"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"noteType":"GENERAL","content":"처음 적은 내용"}"""))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long noteId = om.readTree(created).get("id").asLong();

        mvc.perform(put("/api/v1/phi/nursing-notes/" + noteId)
                        .header("Authorization", bearer("ward01"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"noteType":"GENERAL","content":"고친 내용"}"""))
                .andExpect(status().isOk());

        String detail = jdbcTemplate.queryForObject(
                "select detail::text from phi_access_log where action = 'NOTE_EDIT' "
                        + "and subject_ref = ? order by id desc limit 1",
                String.class, subjectRef);
        org.assertj.core.api.Assertions.assertThat(detail)
                .contains("처음 적은 내용")
                .contains("고친 내용");
    }

    @Test
    void 남이_쓴_기록은_고칠_수_없다_NN_001() throws Exception {
        long noteId = writeNote("ward01").get("id").asLong();

        // 같은 병동 간호사여도 안 된다. 누가 썼는지가 기록의 근거이기 때문이다.
        mvc.perform(editNote(noteId, "head01", "남이 고쳐 본다"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NN-001"));
    }

    @Test
    void _24시간이_지나면_본인도_고칠_수_없다_NN_002() throws Exception {
        long noteId = writeNote("ward01").get("id").asLong();

        // 하루를 기다릴 수는 없으니 작성 시각을 되돌린다.
        // 규칙이 createdAt 하나만 보므로 이것으로 충분하다.
        jdbcTemplate.update(
                "update nursing_note set created_at = created_at - interval '25 hours' where id = ?",
                noteId);

        mvc.perform(editNote(noteId, "ward01", "하루 지나서 고쳐 본다"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("NN-002"));
    }

    @Test
    void 내용이_비면_남길_수_없다_NN_003() throws Exception {
        mvc.perform(post("/api/v1/phi/subjects/" + subjectRef + "/nursing-notes")
                        .header("Authorization", bearer("ward01"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"noteType":"GENERAL","content":"  "}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NN-003"));
    }

    @Test
    void 삭제하는_길이_없다() throws Exception {
        long noteId = writeNote("ward01").get("id").asLong();

        // 규칙 5: 간호기록과 이송 이력은 삭제하지 않는다.
        // 지우는 엔드포인트가 실수로 생기면 여기서 걸린다.
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/phi/nursing-notes/" + noteId)
                        .header("Authorization", bearer("ward01")))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("VAL-002"));
    }

    @Test
    void 관계없는_검사실은_기록을_볼_수_없다() throws Exception {
        writeNote("ward01");

        // 이 환자에게 걸린 요청이 없으므로 MRI실은 아무 관계가 없다
        mvc.perform(get("/api/v1/phi/subjects/" + subjectRef + "/nursing-notes")
                        .header("Authorization", bearer("mri01")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERM-001"));
    }

    @Test
    void 활력징후를_남기고_다시_읽는다() throws Exception {
        mvc.perform(post("/api/v1/phi/subjects/" + subjectRef + "/vital-signs")
                        .header("Authorization", bearer("ward01"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"measuredAt":"%s","sbp":128,"dbp":78,"pulse":92,
                                 "temperature":38.4,"respiration":20,"spo2":96}"""
                                .formatted(OffsetDateTime.now())))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/phi/subjects/" + subjectRef + "/vital-signs")
                        .header("Authorization", bearer("ward01")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].pulse").value(92));
    }

    // ── 도우미 ──────────────────────────────────────────────

    private JsonNode writeNote(String actor) throws Exception {
        String body = mvc.perform(post("/api/v1/phi/subjects/" + subjectRef + "/nursing-notes")
                        .header("Authorization", bearer(actor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"noteType":"HANDOVER",
                                 "situation":"오전 회진 후 발열",
                                 "background":"폐렴으로 입원 이틀째",
                                 "assessment":"항생제 반응 지켜보는 중",
                                 "recommendation":"4시간 간격 체온 확인"}"""))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return om.readTree(body);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
            editNote(long noteId, String actor, String situation) throws Exception {
        return put("/api/v1/phi/nursing-notes/" + noteId)
                .header("Authorization", bearer(actor))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"noteType":"HANDOVER","situation":"%s",
                         "background":"폐렴으로 입원 이틀째",
                         "assessment":"항생제 반응 지켜보는 중",
                         "recommendation":"4시간 간격 체온 확인"}""".formatted(situation));
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
