package com.nursecollab.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nursecollab.domain.encounter.entity.Encounter;
import com.nursecollab.domain.encounter.repository.EncounterRepository;
import com.nursecollab.domain.patient.entity.Patient;
import com.nursecollab.domain.patient.entity.Sex;
import com.nursecollab.domain.patient.repository.PatientRepository;
import com.nursecollab.domain.staff.repository.StaffRepository;
import com.nursecollab.domain.transfer.repository.ExamTypeRepository;
import com.nursecollab.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP 를 통해 검증한다.
 *
 * 서비스를 직접 부르는 테스트로는 필터·컨트롤러·예외 변환을 지나가지 않아
 * 권한 판정과 에러 코드가 실제로 그 값으로 나가는지 알 수 없다.
 * 화면과 문서가 약속하는 것은 서비스 반환값이 아니라 상태 코드와 `code` 다.
 */
class TransferApiTest extends IntegrationTest {

    /** 환자 등록번호가 겹치지 않게 새로 딴다 */
    private static final AtomicInteger PATIENT_SEQ = new AtomicInteger(9000);

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper om;
    @Autowired private StaffRepository staffRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private EncounterRepository encounterRepository;
    @Autowired private ExamTypeRepository examTypeRepository;

    private Long encounterId;
    private Long brainMriId;

    @BeforeEach
    void setUp() {
        // 시드 재원을 쓰면 다른 테스트가 만든 요청 때문에 관계 판정이 흔들린다.
        // 이 클래스가 쓸 재원을 따로 만든다.
        var ward = staffRepository.findByLoginIdWithDepartment("ward01").orElseThrow().getDepartment();
        Patient patient = patientRepository.save(Patient.create(
                "P%07d".formatted(PATIENT_SEQ.getAndIncrement()), "김OO",
                LocalDate.of(1958, 3, 11), Sex.M, null, null));
        encounterId = encounterRepository.save(Encounter.admit(patient, ward, "302", "1",
                OffsetDateTime.now().minusDays(4), "뇌경색", false)).getId();

        brainMriId = examTypeRepository.findAllActiveWithDepartment().stream()
                .filter(e -> e.getCode().equals("MRI_BRAIN"))
                .findFirst().orElseThrow().getId();
    }

    // ── 인증 ────────────────────────────────────────────────

    @Test
    void 토큰_없이_부르면_401() throws Exception {
        mvc.perform(get("/api/v1/encounters"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 비밀번호가_틀리면_AUTH_001() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId":"ward01","password":"틀린비밀번호"}"""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-001"));
    }

    // ── 역할 권한 ───────────────────────────────────────────

    @Test
    void 일반_간호사는_통계를_볼_수_없다() throws Exception {
        mvc.perform(get("/api/v1/stats/waiting-time").header("Authorization", bearer("ward01")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERM-003"));
    }

    @Test
    void 수간호사는_통계를_볼_수_있다() throws Exception {
        mvc.perform(get("/api/v1/stats/waiting-time").header("Authorization", bearer("head01")))
                .andExpect(status().isOk());
    }

    // ── 관계 기반 접근 ──────────────────────────────────────

    @Test
    void 요청이_걸리지_않은_검사실은_환자를_열_수_없다() throws Exception {
        // MRI 로만 요청을 보냈으므로 CT실은 이 환자와 아무 관계가 없다
        createRequest();

        mvc.perform(get("/api/v1/encounters/" + encounterId).header("Authorization", bearer("ct01")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERM-001"));
    }

    @Test
    void 요청이_걸린_검사실은_환자를_열_수_있다() throws Exception {
        createRequest();

        mvc.perform(get("/api/v1/encounters/" + encounterId).header("Authorization", bearer("mri01")))
                .andExpect(status().isOk());
    }

    // ── 전이 규칙 ───────────────────────────────────────────

    @Test
    void 접수는_검사실이_한다_병동이_누르면_PERM_002() throws Exception {
        JsonNode created = createRequest();

        mvc.perform(transition(created, "ward01", """
                        {"toStatus":"ACCEPTED","scheduledAt":"%s","version":%d}"""
                        .formatted(oneHourLater(), created.get("version").asLong())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERM-002"));
    }

    @Test
    void 접수에_예정시각이_없으면_TR_005() throws Exception {
        JsonNode created = createRequest();

        mvc.perform(transition(created, "mri01", """
                        {"toStatus":"ACCEPTED","version":%d}"""
                        .formatted(created.get("version").asLong())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TR-005"));
    }

    @Test
    void 건너뛰는_전이는_TR_001() throws Exception {
        JsonNode created = createRequest();

        // 요청됨 → 검사중 은 규칙표에 없다
        mvc.perform(transition(created, "mri01", """
                        {"toStatus":"IN_PROGRESS","version":%d}"""
                        .formatted(created.get("version").asLong())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TR-001"));
    }

    @Test
    void 보류에_사유가_없으면_TR_003() throws Exception {
        JsonNode created = createRequest();

        mvc.perform(transition(created, "mri01", """
                        {"toStatus":"ON_HOLD","version":%d}"""
                        .formatted(created.get("version").asLong())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TR-003"));
    }

    @Test
    void 낡은_버전으로_보내면_TR_002() throws Exception {
        JsonNode created = createRequest();
        long staleVersion = created.get("version").asLong();

        // 먼저 접수해 버전을 올린다
        mvc.perform(transition(created, "mri01", """
                        {"toStatus":"ACCEPTED","scheduledAt":"%s","version":%d}"""
                        .formatted(oneHourLater(), staleVersion)))
                .andExpect(status().isOk());

        // 접수 전 화면을 그대로 들고 있던 사람이 누른 상황
        mvc.perform(transition(created, "mri01", """
                        {"toStatus":"READY","version":%d}""".formatted(staleVersion)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TR-002"));
    }

    // ── 도우미 ──────────────────────────────────────────────

    private String bearer(String loginId) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId":"%s","password":"nurse1234!"}""".formatted(loginId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + om.readTree(body).get("accessToken").asText();
    }

    private JsonNode createRequest() throws Exception {
        String body = mvc.perform(post("/api/v1/transfer-requests")
                        .header("Authorization", bearer("ward01"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"encounterId":%d,"examTypeId":%d,"priority":"URGENT"}"""
                                .formatted(encounterId, brainMriId)))
                // 생성은 201 이고 Location 헤더가 붙는다
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return om.readTree(body);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder transition(
            JsonNode created, String loginId, String body) throws Exception {
        return post("/api/v1/transfer-requests/" + created.get("id").asLong() + "/transitions")
                .header("Authorization", bearer(loginId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private String oneHourLater() {
        return OffsetDateTime.now().plusHours(1).toString();
    }
}
