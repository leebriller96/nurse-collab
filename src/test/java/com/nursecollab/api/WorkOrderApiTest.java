package com.nursecollab.api;

import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.contains;
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
class WorkOrderApiTest extends IntegrationTest {

    /** 환자 등록번호가 겹치지 않게 새로 딴다 */
    private static final AtomicInteger PATIENT_SEQ = new AtomicInteger(9000);

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper om;
    @Autowired private StaffRepository staffRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private AdmissionService admissionService;
    @Autowired private ServiceItemRepository serviceItemRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Long encounterId;
    private UUID subjectRef;
    private Long brainMriId;

    @BeforeEach
    void setUp() {
        // 시드 재원을 쓰면 다른 테스트가 만든 요청 때문에 관계 판정이 흔들린다.
        // 이 클래스가 쓸 재원을 따로 만든다.
        var ward = staffRepository.findByLoginIdWithDepartment("ward01").orElseThrow().getDepartment();
        Patient patient = patientRepository.save(Patient.create(
                "P%07d".formatted(PATIENT_SEQ.getAndIncrement()), "김OO",
                LocalDate.of(1958, 3, 11), Sex.M, null, null));
        Encounter encounter = admissionService.admit(patient, ward.getId(), "302", "1",
                OffsetDateTime.now().minusDays(4), "뇌경색", false);
        encounterId = encounter.getId();
        subjectRef = encounter.getSubjectRef();

        brainMriId = serviceItemRepository.findAllActiveWithDepartment().stream()
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

    // ── 목록 크기 ───────────────────────────────────────────

    @Test
    void 페이지_크기는_1에서_200_사이로_눌러_담는다() throws Exception {
        // size=0 은 PageRequest 가 거부해 500 이 나갔고, 큰 값은 반년치를 한 번에 올렸다
        mvc.perform(get("/api/v1/work-orders").param("direction", "OUTBOUND").param("size", "0")
                        .header("Authorization", bearer("ward01")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(1));

        mvc.perform(get("/api/v1/work-orders").param("direction", "OUTBOUND").param("size", "100000")
                        .header("Authorization", bearer("ward01")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(200));

        mvc.perform(get("/api/v1/work-orders").param("direction", "OUTBOUND").param("page", "-3")
                        .header("Authorization", bearer("ward01")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0));
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

        mvc.perform(get("/api/v1/phi/subjects/" + subjectRef).header("Authorization", bearer("ct01")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERM-001"));
    }

    @Test
    void 요청이_걸린_검사실은_환자를_열_수_있다() throws Exception {
        createRequest();

        mvc.perform(get("/api/v1/phi/subjects/" + subjectRef).header("Authorization", bearer("mri01")))
                .andExpect(status().isOk());
    }

    // ── 큐 정렬 ─────────────────────────────────────────────

    @Test
    void 들어온_요청은_응급_긴급_일반_순으로_온다() throws Exception {
        // 일부러 응급을 가운데에 만든다. 요청 시각 순으로만 정렬돼도 우연히 통과하지 않게.
        long routine = createRequest("ROUTINE").get("id").asLong();
        long emergency = createRequest("EMERGENCY").get("id").asLong();
        long urgent = createRequest("URGENT").get("id").asLong();

        String body = mvc.perform(get("/api/v1/work-orders")
                        .param("direction", "INBOUND")
                        .param("size", "200")
                        .header("Authorization", bearer("mri01")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        java.util.List<Long> ids = new java.util.ArrayList<>();
        om.readTree(body).get("content").forEach(row -> ids.add(row.get("id").asLong()));

        // 우선순위는 문자열로 저장된다. 그대로 역순 정렬하면 URGENT > ROUTINE > EMERGENCY 가 되어
        // 응급이 맨 아래로 간다. 큐 맨 위에 있어야 할 것이 제일 늦게 눈에 띈다.
        org.assertj.core.api.Assertions.assertThat(ids).contains(routine, emergency, urgent);
        org.assertj.core.api.Assertions.assertThat(ids.indexOf(emergency)).isLessThan(ids.indexOf(urgent));
        org.assertj.core.api.Assertions.assertThat(ids.indexOf(urgent)).isLessThan(ids.indexOf(routine));
    }

    @Test
    void 기간을_주지_않으면_어제_들어와_안_끝난_요청도_큐에_남는다() throws Exception {
        JsonNode open = createRequest("ROUTINE");
        JsonNode cancelled = createRequest("ROUTINE");
        mvc.perform(transition(cancelled, "ward01", """
                        {"toStatus":"CANCELLED","reason":"중복 요청","version":%d}"""
                        .formatted(cancelled.get("version").asLong())))
                .andExpect(status().isOk());

        // 자정을 기다리지 않고 요청 시각만 이틀 전으로 되돌린다. 목록은 이 컬럼 하나로 거른다.
        jdbcTemplate.update("update work_order set requested_at = now() - interval '2 days' where id in (?, ?)",
                open.get("id").asLong(), cancelled.get("id").asLong());

        java.util.List<Long> current = inboundIds(null);
        // 밤 근무조의 큐가 자정에 비면 전날 저녁에 걸린 일이 그대로 잊힌다
        org.assertj.core.api.Assertions.assertThat(current).contains(open.get("id").asLong());
        // 끝난 요청까지 끌고 오면 큐가 반년치 기록으로 찬다
        org.assertj.core.api.Assertions.assertThat(current).doesNotContain(cancelled.get("id").asLong());

        // 날짜를 직접 고르면 그 날짜만 본다. 일정 보드와 지난 요청 조회가 이렇게 부른다.
        org.assertj.core.api.Assertions.assertThat(inboundIds(LocalDate.now().toString()))
                .doesNotContain(open.get("id").asLong());
    }

    private java.util.List<Long> inboundIds(String date) throws Exception {
        var request = get("/api/v1/work-orders")
                .param("direction", "INBOUND")
                .param("size", "200")
                .header("Authorization", bearer("mri01"));
        if (date != null) request.param("from", date).param("to", date);
        String body = mvc.perform(request)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        java.util.List<Long> ids = new java.util.ArrayList<>();
        om.readTree(body).get("content").forEach(row -> ids.add(row.get("id").asLong()));
        return ids;
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
                .andExpect(jsonPath("$.code").value("ORD-005"));
    }

    @Test
    void 건너뛰는_전이는_TR_001() throws Exception {
        JsonNode created = createRequest();

        // 요청됨 → 검사중 은 규칙표에 없다
        mvc.perform(transition(created, "mri01", """
                        {"toStatus":"IN_PROGRESS","version":%d}"""
                        .formatted(created.get("version").asLong())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORD-001"));
    }

    @Test
    void 보류에_사유가_없으면_TR_003() throws Exception {
        JsonNode created = createRequest();

        mvc.perform(transition(created, "mri01", """
                        {"toStatus":"ON_HOLD","version":%d}"""
                        .formatted(created.get("version").asLong())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ORD-003"));
    }

    @Test
    void 검사실이_건_보류는_병동이_풀_수_없다_PERM_002() throws Exception {
        JsonNode created = createRequest();

        mvc.perform(transition(created, "mri01", """
                        {"toStatus":"ON_HOLD","reason":"장비 점검","version":%d}"""
                        .formatted(created.get("version").asLong())))
                .andExpect(status().isOk());

        // 상대 파트에 복귀 버튼이 없는 것을 화면이 아니라 서버가 정한다. 누가 걸었는지도 같이 준다.
        String detail = mvc.perform(get("/api/v1/work-orders/" + created.get("id").asLong())
                        .header("Authorization", bearer("ward01")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.holdByDepartment.name").value("MRI실"))
                .andExpect(jsonPath("$.availableTransitions[*].status").value(contains("CANCELLED")))
                .andReturn().getResponse().getContentAsString();
        long version = om.readTree(detail).get("version").asLong();

        mvc.perform(transition(created, "ward01", """
                        {"toStatus":"REQUESTED","version":%d}""".formatted(version)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERM-002"));

        // 같은 파트의 다른 사람은 푼다 — 교대 근무자
        mvc.perform(transition(created, "mri01", """
                        {"toStatus":"REQUESTED","version":%d}""".formatted(version)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REQUESTED"));
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
                .andExpect(jsonPath("$.code").value("ORD-002"));
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
        return createRequest("URGENT");
    }

    private JsonNode createRequest(String priority) throws Exception {
        String body = mvc.perform(post("/api/v1/work-orders")
                        .header("Authorization", bearer("ward01"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subjectRef":"%s","serviceItemId":%d,"priority":"%s"}"""
                                .formatted(subjectRef, brainMriId, priority)))
                // 생성은 201 이고 Location 헤더가 붙는다
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return om.readTree(body);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder transition(
            JsonNode created, String loginId, String body) throws Exception {
        return post("/api/v1/work-orders/" + created.get("id").asLong() + "/transitions")
                .header("Authorization", bearer(loginId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private String oneHourLater() {
        return OffsetDateTime.now().plusHours(1).toString();
    }
}
