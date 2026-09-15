package com.nursecollab.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nursecollab.domain.encounter.entity.Encounter;
import com.nursecollab.domain.encounter.service.AdmissionService;
import com.nursecollab.domain.patient.entity.Patient;
import com.nursecollab.domain.patient.entity.Sex;
import com.nursecollab.domain.patient.repository.PatientRepository;
import com.nursecollab.domain.phi.entity.PhiAccessLog;
import com.nursecollab.domain.phi.repository.PhiAccessLogRepository;
import com.nursecollab.domain.staff.repository.StaffRepository;
import com.nursecollab.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 원내가 스스로 남기는 접근 기록과 조회량 제한.
 *
 * 클라우드가 뚫리면 간호사 토큰을 새로 발급해 원내에 물어볼 수 있다.
 * 인증 서버가 클라우드인 이상 이 구멍은 구조적으로 남는다 — 막지는 못한다.
 * 할 수 있는 것은 <b>한 번에 많이 긁어가는 것을 막고 흔적을 남기는 것</b>이고,
 * 그 두 가지가 실제로 되는지 여기서 본다.
 */
class PhiAccessApiTest extends IntegrationTest {

    private static final AtomicInteger SEQ = new AtomicInteger(21000);

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper om;
    @Autowired private StaffRepository staffRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private AdmissionService admissionService;
    @Autowired private PhiAccessLogRepository phiAccessLogRepository;

    private UUID subjectRef;
    private Long patientId;

    @BeforeEach
    void setUp() {
        var ward = staffRepository.findByLoginIdWithDepartment("ward01").orElseThrow().getDepartment();
        Patient patient = patientRepository.save(Patient.create(
                "P%07d".formatted(SEQ.getAndIncrement()), "한OO",
                LocalDate.of(1969, 4, 5), Sex.M, null, null));
        patientId = patient.getId();

        Encounter encounter = admissionService.admit(patient, ward.getId(), "307", "1",
                OffsetDateTime.now().minusDays(1), "복막염", true);
        subjectRef = encounter.getSubjectRef();
    }

    @Test
    void 사람을_열면_원내에_기록이_남는다() throws Exception {
        mvc.perform(get("/api/v1/phi/subjects/" + subjectRef)
                        .header("Authorization", bearer("ward01")))
                .andExpect(status().isOk());

        assertThat(logsFor(subjectRef))
                .anySatisfy(entry -> {
                    assertThat(entry.isGranted()).isTrue();
                    assertThat(entry.getAction()).isEqualTo("VIEW");
                    assertThat(entry.getActorLoginId()).isEqualTo("ward01");
                    assertThat(entry.getPatientId()).isEqualTo(patientId);
                });
    }

    @Test
    void 거절된_시도도_남는다() throws Exception {
        // 업무 쪽 감사 AOP 는 성공한 요청만 적는다. 그런데 조사할 때 제일 보고 싶은 것은
        // "누가 볼 수 없는 것을 열려고 했는가" 다. 그래서 원내가 따로 남긴다.
        mvc.perform(get("/api/v1/phi/subjects/" + subjectRef)
                        .header("Authorization", bearer("ct01")))
                .andExpect(status().isForbidden());

        assertThat(logsFor(subjectRef))
                .anySatisfy(entry -> {
                    assertThat(entry.isGranted()).isFalse();
                    assertThat(entry.getDeniedReason()).isEqualTo("NOT_RELATED");
                    assertThat(entry.getActorLoginId()).isEqualTo("ct01");
                });
    }

    @Test
    void 짧은_시간에_너무_많은_사람을_열면_막는다_PHI_001() throws Exception {
        // 서른 명을 실제로 열어 보려면 환자를 서른 명 만들어야 한다.
        // 규칙이 보는 것은 "최근 10분 안의 서로 다른 환자 수" 하나뿐이라
        // 그 기록을 직접 심어 같은 상황을 만든다.
        // (24시간 수정 창을 created_at 으로 되돌려 확인하는 것과 같은 방식이다.)
        var ward01 = staffRepository.findByLoginIdWithDepartment("ward01").orElseThrow();
        for (int i = 0; i < 30; i++) {
            phiAccessLogRepository.save(PhiAccessLog.granted(
                    ward01.getId(), "ward01", ward01.getDepartment().getId(),
                    UUID.randomUUID(), 900_000L + i, "VIEW", "127.0.0.1", "test"));
        }

        mvc.perform(get("/api/v1/phi/subjects/" + subjectRef)
                        .header("Authorization", bearer("ward01")))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("PHI-001"));
    }

    @Test
    void 막힌_것도_기록에_남는다() throws Exception {
        var ward02 = staffRepository.findByLoginIdWithDepartment("ward02").orElseThrow();
        for (int i = 0; i < 30; i++) {
            phiAccessLogRepository.save(PhiAccessLog.granted(
                    ward02.getId(), "ward02", ward02.getDepartment().getId(),
                    UUID.randomUUID(), 800_000L + i, "VIEW", "127.0.0.1", "test"));
        }

        // ward02 는 이 환자의 병동이 아니므로 관계 판정에서 먼저 막힌다.
        // 제한에 걸린 상황을 보려면 자기 병동 환자를 열어야 한다.
        var ward = ward02.getDepartment();
        Patient patient = patientRepository.save(Patient.create(
                "P%07d".formatted(SEQ.getAndIncrement()), "조OO",
                LocalDate.of(1974, 8, 8), Sex.F, null, null));
        UUID ownRef = admissionService.admit(patient, ward.getId(), "505", "1",
                OffsetDateTime.now().minusDays(1), "고혈압", true).getSubjectRef();

        mvc.perform(get("/api/v1/phi/subjects/" + ownRef)
                        .header("Authorization", bearer("ward02")))
                .andExpect(status().isTooManyRequests());

        assertThat(logsFor(ownRef))
                .anySatisfy(entry -> {
                    assertThat(entry.isGranted()).isFalse();
                    assertThat(entry.getDeniedReason()).isEqualTo("RATE_LIMITED");
                });
    }

    @Test
    void 목록_이름_채우기는_제한에_걸리지_않는다() throws Exception {
        // 목록은 "여는 것" 이 아니다. 그리고 볼 자격이 없는 가명은 애초에 빠지므로
        // 이 통로로는 자기 관계 밖의 사람을 긁어갈 수 없다.
        var ward01 = staffRepository.findByLoginIdWithDepartment("ward01").orElseThrow();
        for (int i = 0; i < 30; i++) {
            phiAccessLogRepository.save(PhiAccessLog.granted(
                    ward01.getId(), "ward01", ward01.getDepartment().getId(),
                    UUID.randomUUID(), 700_000L + i, "VIEW", "127.0.0.1", "test"));
        }

        mvc.perform(post("/api/v1/phi/subjects/brief")
                        .header("Authorization", bearer("ward01"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[\"%s\"]".formatted(subjectRef)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("한OO"));
    }

    // ── 도우미 ──────────────────────────────────────────────

    private List<PhiAccessLog> logsFor(UUID ref) {
        return phiAccessLogRepository.findAll().stream()
                .filter(l -> l.getSubjectRef().equals(ref))
                .toList();
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
