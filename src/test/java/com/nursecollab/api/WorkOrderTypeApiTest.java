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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 이송 말고 다른 종류의 업무가 실제로 도는지 HTTP 로 확인한다.
 *
 * 규칙표 자체는 OrderTypeTest 가 본다. 여기서 보는 것은 그 표가
 * 컨트롤러·검증·예외 변환을 지나 약속한 상태 코드로 나가는가다.
 *
 * 특히 <b>환자가 없는 업무</b>가 중요하다. 이송만 있을 때는 모든 요청에 환자가 있어서
 * 재원 정보를 그냥 꺼내 쓰는 코드가 곳곳에 있었다. 장비 수리가 그 자리를 전부 밟는다.
 */
class WorkOrderTypeApiTest extends IntegrationTest {

    private static final AtomicInteger SEQ = new AtomicInteger(11000);

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper om;
    @Autowired private StaffRepository staffRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private AdmissionService admissionService;

    private Long encounterId;
    private UUID subjectRef;
    private Long repairItemId;      // 장비 수리 (환자 없음)
    private Long bloodTestItemId;   // 검체 (환자 있음)
    private String biomedLoginId;   // 의공학팀 담당자

    @BeforeEach
    void setUp() throws Exception {
        int n = SEQ.getAndIncrement();

        var ward = staffRepository.findByLoginIdWithDepartment("ward01").orElseThrow().getDepartment();
        Patient patient = patientRepository.save(Patient.create(
                "P%07d".formatted(n), "박OO",
                LocalDate.of(1971, 6, 2), Sex.F, null, null));
        Encounter encounter = admissionService.admit(patient, ward, "305", "2",
                OffsetDateTime.now().minusDays(2), "폐렴", true);
        encounterId = encounter.getId();
        subjectRef = encounter.getSubjectRef();

        Long biomedDeptId = createDepartment("BIO" + n, "의공학팀" + n, "BIOMED");
        Long labDeptId = createDepartment("LAB" + n, "진단검사의학과" + n, "LAB");

        biomedLoginId = "biomed" + n;
        createStaff(biomedLoginId, "EMP" + n, "정OO", biomedDeptId);

        repairItemId = createServiceItem("FIX_PUMP" + n, "수액펌프 수리", "EQUIPMENT", biomedDeptId);
        bloodTestItemId = createServiceItem("CBC" + n, "일반혈액검사", "SPECIMEN", labDeptId);
    }

    // ── 환자가 없는 업무 ────────────────────────────────────

    @Test
    void 장비_수리는_환자_없이_요청할_수_있다() throws Exception {
        mvc.perform(post("/api/v1/work-orders")
                        .header("Authorization", bearer("ward01"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"serviceItemId":%d,"priority":"ROUTINE","note":"3번 침대 수액펌프 알람"}"""
                                .formatted(repairItemId)))
                .andExpect(status().isCreated())
                // 번호만 보고 어떤 업무인지 알 수 있어야 한다
                .andExpect(jsonPath("$.requestNo").value(org.hamcrest.Matchers.startsWith("EQ")));
    }

    @Test
    void 장비_수리에_환자를_붙이면_거부한다_ORD_007() throws Exception {
        // 환자 정보 접근은 "진행 중인 요청이 걸려 있는가" 로 판정한다.
        // 장비 수리에 환자를 매달 수 있으면 의공학팀이 그 환자의 기록까지 열게 된다.
        mvc.perform(post("/api/v1/work-orders")
                        .header("Authorization", bearer("ward01"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subjectRef":"%s","serviceItemId":%d,"priority":"ROUTINE"}"""
                                .formatted(subjectRef, repairItemId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ORD-007"));
    }

    @Test
    void 환자가_필요한_업무를_환자_없이_요청하면_거부한다_ORD_006() throws Exception {
        mvc.perform(post("/api/v1/work-orders")
                        .header("Authorization", bearer("ward01"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"serviceItemId":%d,"priority":"ROUTINE"}""".formatted(bloodTestItemId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ORD-006"));
    }

    @Test
    void 환자_없는_요청도_상세_조회가_된다() throws Exception {
        JsonNode created = createEquipmentOrder();

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/work-orders/" + created.get("id").asLong())
                        .header("Authorization", bearer(biomedLoginId)))
                .andExpect(status().isOk())
                // 환자 자리가 비어 있어도 화면이 열려야 한다. 여기서 터지면 큐 전체가 죽는다.
                .andExpect(jsonPath("$.episode").doesNotExist())
                .andExpect(jsonPath("$.orderType").value("EQUIPMENT"));
    }

    // ── 종류마다 갈 수 있는 길이 다르다 ─────────────────────

    @Test
    void 검체는_준비완료로_갈_수_없다_ORD_001() throws Exception {
        // 이송에는 있는 전이지만 검체에는 없다. 환자가 움직이지 않기 때문이다.
        JsonNode created = createSpecimenOrder();

        mvc.perform(post("/api/v1/work-orders/" + created.get("id").asLong() + "/transitions")
                        .header("Authorization", bearer("ward01"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"toStatus":"READY","version":%d}""".formatted(created.get("version").asLong())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORD-001"));
    }

    @Test
    void 장비_수리는_접수할_때_예정시각을_요구하지_않는다() throws Exception {
        // 이송은 접수에 예정시각이 필수다(ORD-005). 종류마다 다르다는 것을 여기서 본다.
        JsonNode created = createEquipmentOrder();

        mvc.perform(post("/api/v1/work-orders/" + created.get("id").asLong() + "/transitions")
                        .header("Authorization", bearer(biomedLoginId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"toStatus":"ACCEPTED","version":%d}""".formatted(created.get("version").asLong())))
                .andExpect(status().isOk());
    }

    @Test
    void 장비_수리는_부품대기로_갈_때_사유가_필수다_ORD_003() throws Exception {
        JsonNode order = createEquipmentOrder();
        long id = order.get("id").asLong();

        long version = transition(id, biomedLoginId, "ACCEPTED", order.get("version").asLong(), null);
        version = transition(id, biomedLoginId, "IN_PROGRESS", version, null);

        // 어떤 부품을 기다리는지 적지 않으면 언제 끝날지 아무도 모른다
        mvc.perform(post("/api/v1/work-orders/" + id + "/transitions")
                        .header("Authorization", bearer(biomedLoginId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"toStatus":"AWAITING_PARTS","version":%d}""".formatted(version)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ORD-003"));
    }

    @Test
    void 장비_수리는_수행_파트가_스스로_완료한다() throws Exception {
        JsonNode order = createEquipmentOrder();
        long id = order.get("id").asLong();

        long version = transition(id, biomedLoginId, "ACCEPTED", order.get("version").asLong(), null);
        version = transition(id, biomedLoginId, "IN_PROGRESS", version, null);
        version = transition(id, biomedLoginId, "AWAITING_PARTS", version, "모터 입고 대기");
        version = transition(id, biomedLoginId, "IN_PROGRESS", version, null);

        mvc.perform(post("/api/v1/work-orders/" + id + "/transitions")
                        .header("Authorization", bearer(biomedLoginId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"toStatus":"COMPLETED","version":%d}""".formatted(version)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void 같은_상태라도_종류마다_부르는_이름이_다르게_내려온다() throws Exception {
        JsonNode order = createEquipmentOrder();
        long id = order.get("id").asLong();

        long version = transition(id, biomedLoginId, "ACCEPTED", order.get("version").asLong(), null);
        transition(id, biomedLoginId, "IN_PROGRESS", version, null);

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/work-orders/" + id)
                        .header("Authorization", bearer(biomedLoginId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                // 이송이면 "검사중" 인 자리다. 화면이 표를 따로 들지 않도록 서버가 이름을 준다.
                .andExpect(jsonPath("$.statusLabel").value("수리중"));
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

    private Long createDepartment(String code, String name, String deptType) throws Exception {
        String body = mvc.perform(post("/api/v1/departments")
                        .header("Authorization", bearer("admin01"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"%s","name":"%s","deptType":"%s"}"""
                                .formatted(code, name, deptType)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return om.readTree(body).get("id").asLong();
    }

    private void createStaff(String loginId, String employeeNo, String name, Long deptId)
            throws Exception {
        mvc.perform(post("/api/v1/staff")
                        .header("Authorization", bearer("admin01"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId":"%s","password":"nurse1234!","employeeNo":"%s",
                                 "name":"%s","role":"NURSE","departmentId":%d}"""
                                .formatted(loginId, employeeNo, name, deptId)))
                .andExpect(status().isOk());
    }

    private Long createServiceItem(String code, String name, String orderType, Long deptId)
            throws Exception {
        String body = mvc.perform(post("/api/v1/service-items")
                        .header("Authorization", bearer("admin01"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"%s","name":"%s","orderType":"%s",
                                 "departmentId":%d,"defaultDuration":30}"""
                                .formatted(code, name, orderType, deptId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderType").value(orderType))
                .andReturn().getResponse().getContentAsString();
        return om.readTree(body).get("id").asLong();
    }

    private JsonNode createEquipmentOrder() throws Exception {
        String body = mvc.perform(post("/api/v1/work-orders")
                        .header("Authorization", bearer("ward01"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"serviceItemId":%d,"priority":"ROUTINE"}""".formatted(repairItemId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return om.readTree(body);
    }

    private JsonNode createSpecimenOrder() throws Exception {
        String body = mvc.perform(post("/api/v1/work-orders")
                        .header("Authorization", bearer("ward01"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subjectRef":"%s","serviceItemId":%d,"priority":"ROUTINE"}"""
                                .formatted(subjectRef, bloodTestItemId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return om.readTree(body);
    }

    /** 전이 후 새 version 을 돌려준다. 다음 전이가 그 값을 써야 낙관적 락에 걸리지 않는다. */
    private long transition(long id, String loginId, String toStatus, long version, String reason)
            throws Exception {
        String payload = reason == null
                ? """
                  {"toStatus":"%s","version":%d}""".formatted(toStatus, version)
                : """
                  {"toStatus":"%s","version":%d,"reason":"%s"}""".formatted(toStatus, version, reason);

        String body = mvc.perform(post("/api/v1/work-orders/" + id + "/transitions")
                        .header("Authorization", bearer(loginId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return om.readTree(body).get("version").asLong();
    }
}
