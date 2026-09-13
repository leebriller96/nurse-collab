package com.nursecollab.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nursecollab.domain.encounter.entity.Encounter;
import com.nursecollab.domain.encounter.service.AdmissionService;
import com.nursecollab.domain.episode.service.LocalWorkRelationAdapter;
import com.nursecollab.domain.patient.entity.Patient;
import com.nursecollab.domain.patient.entity.Sex;
import com.nursecollab.domain.patient.repository.PatientRepository;
import com.nursecollab.domain.phi.port.HttpWorkRelationAdapter;
import com.nursecollab.domain.staff.repository.StaffRepository;
import com.nursecollab.domain.workorder.repository.ServiceItemRepository;
import com.nursecollab.global.error.BusinessException;
import com.nursecollab.global.error.ErrorCode;
import com.nursecollab.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 원내가 업무 서버에 묻는 통로를 <b>실제 HTTP 로</b> 확인한다.
 *
 * 두 쪽이 같은 JSON 모양을 각자 레코드로 들고 있다. 원내 코드가 업무 쪽 DTO 를
 * 가져다 쓰면 경계가 새기 때문이다. 대가로, 한쪽 필드 이름만 바뀌면 컴파일은 되는데
 * 값이 조용히 비어 온다 — "관계 없음" 으로. 그러면 검사실이 방금 받은 환자를 못 연다.
 *
 * MockMvc 로는 이걸 잡을 수 없다. 원내 쪽 클라이언트가 직렬화한 몸통이
 * 업무 쪽 컨트롤러에 실제로 도착해야 하므로 포트를 열고 진짜 어댑터로 부른다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WorkRelationContractTest extends IntegrationTest {

    private static final AtomicInteger PATIENT_SEQ = new AtomicInteger(31000);

    @LocalServerPort private int port;
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper om;
    @Autowired private StaffRepository staffRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private AdmissionService admissionService;
    @Autowired private ServiceItemRepository serviceItemRepository;
    @Autowired private LocalWorkRelationAdapter local;
    @Autowired private JdbcTemplate jdbcTemplate;

    private HttpWorkRelationAdapter http;
    private UUID subjectRef;
    private Long wardId;
    private Long mriId;
    private Long ctId;

    @BeforeEach
    void setUp() throws Exception {
        http = new HttpWorkRelationAdapter("http://localhost:" + port,
                Duration.ofSeconds(2), Duration.ofSeconds(3));

        wardId = departmentOf("ward01");
        mriId = departmentOf("mri01");
        ctId = departmentOf("ct01");

        Patient patient = patientRepository.save(Patient.create(
                "P%07d".formatted(PATIENT_SEQ.getAndIncrement()), "문OO",
                LocalDate.of(1961, 5, 2), Sex.F, null, null));
        Encounter encounter = admissionService.admit(patient, wardId, "305", "2",
                OffsetDateTime.now().minusDays(1), "요추 협착", true);
        subjectRef = encounter.getSubjectRef();

        // 병동이 MRI 로 요청을 건다. 이제 MRI실은 이 사람과 관계가 있고 CT실은 없다.
        Long brainMri = serviceItemRepository.findAllActiveWithDepartment().stream()
                .filter(e -> e.getCode().equals("MRI_BRAIN"))
                .findFirst().orElseThrow().getId();
        mvc.perform(post("/api/v1/work-orders")
                        .header("Authorization", bearer("ward01"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subjectRef":"%s","serviceItemId":%d,"priority":"ROUTINE"}"""
                                .formatted(subjectRef, brainMri)))
                .andExpect(status().isCreated());
    }

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void 요청이_걸린_파트에는_HTTP_로도_같은_답이_온다() throws Exception {
        actingAs("mri01");
        UUID unrelated = UUID.randomUUID();

        assertThat(http.hasActiveOrderTo(subjectRef, mriId)).isTrue();
        assertThat(http.subjectsWithActiveOrdersTo(List.of(subjectRef, unrelated), mriId))
                .containsExactly(subjectRef);

        // 한 서버일 때와 두 서버일 때 볼 수 있는 사람이 달라지면 안 된다
        assertThat(http.subjectsWithActiveOrdersTo(List.of(subjectRef, unrelated), mriId))
                .isEqualTo(local.subjectsWithActiveOrdersTo(List.of(subjectRef, unrelated), mriId));
    }

    @Test
    void 요청이_없는_파트에는_없다고_답한다() throws Exception {
        actingAs("ct01");

        assertThat(http.hasActiveOrderTo(subjectRef, ctId)).isFalse();
    }

    @Test
    void 남의_파트에_대해_물으면_PERM_001() throws Exception {
        // MRI실 토큰으로 CT실에 무엇이 걸려 있는지 알아낼 수 없어야 한다
        actingAs("mri01");

        assertThatThrownBy(() -> http.hasActiveOrderTo(subjectRef, ctId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_RELATED_DEPARTMENT);
    }

    @Test
    void 침대_등록은_다시_보내도_한_번만_남는다() throws Exception {
        actingAs("ward01");
        UUID ref = UUID.randomUUID();
        OffsetDateTime admittedAt = OffsetDateTime.now().minusHours(3);

        http.registerEpisode(ref, wardId, "306", "1", admittedAt);
        // 첫 요청이 응답 직전에 끊겼다고 치고 다시 보낸다
        http.registerEpisode(ref, wardId, "306", "1", admittedAt);

        Integer rows = jdbcTemplate.queryForObject(
                "select count(*) from care_episode where subject_ref = ?", Integer.class, ref);
        assertThat(rows).isEqualTo(1);
    }

    @Test
    void 남의_병동에_침대를_등록할_수_없다() throws Exception {
        actingAs("mri01");

        assertThatThrownBy(() -> http.registerEpisode(
                UUID.randomUUID(), wardId, "307", "1", OffsetDateTime.now()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_RELATED_DEPARTMENT);
    }

    @Test
    void 업무_서버에_닿지_못하면_관계_없음이_아니라_PHI_002() throws Exception {
        actingAs("mri01");
        // 1번 포트는 열려 있지 않다
        HttpWorkRelationAdapter unreachable = new HttpWorkRelationAdapter("http://localhost:1",
                Duration.ofMillis(500), Duration.ofMillis(500));

        assertThatThrownBy(() -> unreachable.hasActiveOrderTo(subjectRef, mriId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.WORK_RELATION_UNAVAILABLE);
    }

    @Test
    void 사용자_요청_밖에서는_토큰을_지어내지_않고_실패한다() {
        // 요청 문맥이 없다. 서버용 자격증명으로 대신하는 것이 이 설계가 막으려는 구멍이다.
        assertThatThrownBy(() -> http.hasActiveOrderTo(subjectRef, mriId))
                .isInstanceOf(IllegalStateException.class);
    }

    // ── 도우미 ──────────────────────────────────────────────

    /** 원내 서버가 이 사람의 요청을 처리하는 중인 것처럼 요청 문맥을 세운다 */
    private void actingAs(String loginId) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", bearer(loginId));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
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
}
