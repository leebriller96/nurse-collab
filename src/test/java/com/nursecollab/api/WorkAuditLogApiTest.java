package com.nursecollab.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nursecollab.domain.staff.repository.StaffRepository;
import com.nursecollab.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 업무 쪽 감사 기록 — 로그인과 기준 정보 변경.
 *
 * 한동안 이 테이블에 쓰는 곳이 하나도 없었다. 환자 열람 기록을 원내로 옮기고 나니
 * 남은 것이 없었던 것이다. 조용히 안 쌓이면 아무도 모르므로 실제 요청으로 확인한다.
 */
class WorkAuditLogApiTest extends IntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper om;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private StaffRepository staffRepository;

    // ── 로그인 ──────────────────────────────────────────────

    @Test
    void 없는_아이디로_로그인하면_누가_무엇을_두드려_봤는지_남는다() throws Exception {
        String attempted = "nobody-" + UUID.randomUUID().toString().substring(0, 8);

        login(attempted, "nurse1234!")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-001"));

        Map<String, Object> row = jdbcTemplate.queryForMap("""
                select action, actor_id, detail->>'reason' as reason from audit_log
                where detail->>'loginId' = ? order by id desc limit 1
                """, attempted);
        assertThat(row.get("action")).isEqualTo("LOGIN_FAILED");
        assertThat(row.get("actor_id")).isNull();
        assertThat(row.get("reason")).isEqualTo("UNKNOWN_ID");

        // 화면이 읽는 응답에도 실린다
        mvc.perform(get("/api/v1/audit-logs")
                        .param("from", LocalDate.now().toString())
                        .param("size", "50")
                        .header("Authorization", bearer("admin01")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.detail.loginId == '%s')].action".formatted(attempted))
                        .value(hasItem("LOGIN_FAILED")));
    }

    @Test
    void 비밀번호가_틀리면_그_계정의_실패로_남고_비밀번호는_어디에도_남지_않는다() throws Exception {
        Long ward02 = staffRepository.findByLoginIdWithDepartment("ward02").orElseThrow().getId();
        String wrongPassword = "wrong-" + UUID.randomUUID();

        // 화면에는 없는 아이디와 같은 코드가 간다. 계정이 있는지 알려 주지 않는다.
        login("ward02", wrongPassword)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-001"));

        Map<String, Object> row = jdbcTemplate.queryForMap("""
                select action, detail->>'reason' as reason from audit_log
                where actor_id = ? order by id desc limit 1
                """, ward02);
        assertThat(row.get("action")).isEqualTo("LOGIN_FAILED");
        assertThat(row.get("reason")).isEqualTo("BAD_PASSWORD");

        assertThat(leaked(wrongPassword)).isZero();
    }

    @Test
    void 거듭_틀리면_맞는_비밀번호로도_한동안_열리지_않고_그것도_남는다() throws Exception {
        // 다른 테스트가 쓰는 계정을 잠그면 안 된다. 이 테스트만의 계정을 만든다.
        String loginId = "lock" + UUID.randomUUID().toString().substring(0, 6);
        jdbcTemplate.update("""
                insert into staff (login_id, password_hash, employee_no, name, role, department_id)
                select ?, s.password_hash, ?, '잠금시험', 'NURSE', s.department_id
                  from staff s where s.login_id = 'ward02'""", loginId, "E" + loginId);

        for (int i = 0; i < 10; i++) {
            login(loginId, "wrong-" + i).andExpect(status().isUnauthorized());
        }

        // 맞는 비밀번호도 같은 답이다. 그래야 대입이 멈춘다.
        login(loginId, "nurse1234!")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("AUTH-004"));

        Map<String, Object> row = jdbcTemplate.queryForMap("""
                select detail->>'reason' as reason from audit_log
                where detail->>'loginId' = ? order by id desc limit 1
                """, loginId);
        assertThat(row.get("reason")).isEqualTo("LOCKED");

        // 다른 계정은 영향이 없다
        login("ward02", "nurse1234!").andExpect(status().isOk());
    }

    @Test
    void 로그인과_로그아웃이_남는다() throws Exception {
        Long ct01 = staffRepository.findByLoginIdWithDepartment("ct01").orElseThrow().getId();

        String token = bearer("ct01");
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", token))
                .andExpect(status().isNoContent());

        List<String> actions = jdbcTemplate.queryForList(
                "select action from audit_log where actor_id = ? order by id desc limit 2",
                String.class, ct01);
        assertThat(actions).containsExactly("LOGOUT", "LOGIN");
    }

    // ── 기준 정보 ───────────────────────────────────────────

    @Test
    void 직원을_만들고_역할을_바꾸면_누가_무엇에서_무엇으로_바꿨는지_남는다() throws Exception {
        Long admin = staffRepository.findByLoginIdWithDepartment("admin01").orElseThrow().getId();
        Long ward = staffRepository.findByLoginIdWithDepartment("ward01").orElseThrow().getDepartment().getId();
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        String initialPassword = "init-" + suffix + "-pass";

        String created = mvc.perform(post("/api/v1/staff")
                        .header("Authorization", bearer("admin01"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId":"audit%s","password":"%s","employeeNo":"EA%s",
                                 "name":"감사대상","role":"NURSE","departmentId":%d}"""
                                .formatted(suffix, initialPassword, suffix, ward)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long staffId = om.readTree(created).get("id").asLong();

        // 만들기는 경로에 id 가 없다. 응답에서 꺼내 남겼는지 본다.
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from audit_log
                where action = 'CREATE' and target_type = 'STAFF' and target_id = ? and actor_id = ?
                """, Integer.class, staffId, admin)).isEqualTo(1);

        mvc.perform(put("/api/v1/staff/" + staffId)
                        .header("Authorization", bearer("admin01"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"감사대상","role":"HEAD_NURSE","departmentId":%d}""".formatted(ward)))
                .andExpect(status().isOk());

        Map<String, Object> row = jdbcTemplate.queryForMap("""
                select actor_id, detail->'before'->>'role' as before_role, detail->'after'->>'role' as after_role
                from audit_log where action = 'UPDATE' and target_type = 'STAFF' and target_id = ?
                order by id desc limit 1
                """, staffId);
        assertThat(((Number) row.get("actor_id")).longValue()).isEqualTo(admin);
        assertThat(row.get("before_role")).isEqualTo("NURSE");
        assertThat(row.get("after_role")).isEqualTo("HEAD_NURSE");

        // 초기 비밀번호는 요청 본문에만 있었다. 기록에 새면 안 된다.
        assertThat(leaked(initialPassword)).isZero();
    }

    @Test
    void 바뀐_것이_없으면_수정을_남기지_않는다() throws Exception {
        var head = staffRepository.findByLoginIdWithDepartment("head01").orElseThrow();

        mvc.perform(put("/api/v1/staff/" + head.getId())
                        .header("Authorization", bearer("admin01"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","role":"%s","departmentId":%d,"phone":%s}"""
                                .formatted(head.getName(), head.getRole().name(),
                                        head.getDepartment().getId(),
                                        head.getPhone() == null ? "null" : "\"" + head.getPhone() + "\"")))
                .andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from audit_log
                where action = 'UPDATE' and target_type = 'STAFF' and target_id = ?
                """, Integer.class, head.getId())).isZero();
    }

    @Test
    void 부서를_만들고_내리면_남는다() throws Exception {
        String code = "Q" + UUID.randomUUID().toString().substring(0, 5).toUpperCase();

        String created = mvc.perform(post("/api/v1/departments")
                        .header("Authorization", bearer("admin01"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"%s","name":"감사확인병동","deptType":"WARD"}""".formatted(code)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long departmentId = om.readTree(created).get("id").asLong();

        mvc.perform(patch("/api/v1/departments/" + departmentId + "/deactivate")
                        .header("Authorization", bearer("admin01")))
                .andExpect(status().isNoContent());

        List<String> actions = jdbcTemplate.queryForList("""
                select action from audit_log where target_type = 'DEPARTMENT' and target_id = ?
                order by id
                """, String.class, departmentId);
        assertThat(actions).containsExactly("CREATE", "DEACTIVATE");
    }

    @Test
    void 관리자가_아니면_바꾸지_못하고_기록도_남지_않는다() throws Exception {
        Long ward01 = staffRepository.findByLoginIdWithDepartment("ward01").orElseThrow().getId();

        mvc.perform(post("/api/v1/departments")
                        .header("Authorization", bearer("ward01"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"NOPE1\",\"name\":\"몰래\",\"deptType\":\"WARD\"}"))
                .andExpect(status().isForbidden());

        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from audit_log where target_type = 'DEPARTMENT' and actor_id = ?
                """, Integer.class, ward01)).isZero();
    }

    // ── 도우미 ──────────────────────────────────────────────

    private org.springframework.test.web.servlet.ResultActions login(String loginId, String password)
            throws Exception {
        return mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("loginId", loginId, "password", password))));
    }

    private int leaked(String secret) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from audit_log where detail::text like ?", Integer.class, "%" + secret + "%");
        return count == null ? 0 : count;
    }

    private String bearer(String loginId) throws Exception {
        String body = login(loginId, "nurse1234!")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + om.readTree(body).get("accessToken").asText();
    }
}
