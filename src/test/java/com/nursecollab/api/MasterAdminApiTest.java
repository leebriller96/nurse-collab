package com.nursecollab.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nursecollab.domain.staff.repository.StaffRepository;
import com.nursecollab.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 기준 정보는 조회는 누구나 하지만 변경은 관리자만 한다.
 * 이 판정이 컨트롤러에 있어서 서비스 테스트로는 지나가지 않는다.
 */
class MasterAdminApiTest extends IntegrationTest {

    /** 코드와 아이디가 겹치지 않게 새로 딴다 */
    private static final AtomicInteger SEQ = new AtomicInteger(1);

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper om;
    @Autowired private StaffRepository staffRepository;

    @Test
    void 일반_간호사는_부서를_만들_수_없다() throws Exception {
        mvc.perform(createDepartment("ward01", "X" + SEQ.getAndIncrement()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERM-003"));
    }

    @Test
    void 수간호사도_부서를_만들_수_없다() throws Exception {
        // 통계는 볼 수 있지만 기준 정보는 못 바꾼다. 둘은 다른 권한이다.
        mvc.perform(createDepartment("head01", "X" + SEQ.getAndIncrement()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERM-003"));
    }

    @Test
    void 관리자는_부서를_만들_수_있다() throws Exception {
        mvc.perform(createDepartment("admin01", "X" + SEQ.getAndIncrement()))
                .andExpect(status().isOk());
    }

    @Test
    void 이미_쓰는_부서_코드는_MST_001() throws Exception {
        String code = "X" + SEQ.getAndIncrement();
        mvc.perform(createDepartment("admin01", code)).andExpect(status().isOk());

        mvc.perform(createDepartment("admin01", code))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MST-001"));
    }

    @Test
    void 비밀번호가_8자_미만이면_VAL_001() throws Exception {
        mvc.perform(post("/api/v1/staff")
                        .header("Authorization", bearer("admin01"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId":"short%d","password":"1234","employeeNo":"E%d",
                                 "name":"김OO","role":"NURSE","departmentId":%d}"""
                                .formatted(SEQ.get(), SEQ.getAndIncrement(), wardDepartmentId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VAL-001"));
    }

    @Test
    void 사용_중지된_계정은_로그인할_수_없다() throws Exception {
        int n = SEQ.getAndIncrement();
        String loginId = "temp%d".formatted(n);

        String created = mvc.perform(post("/api/v1/staff")
                        .header("Authorization", bearer("admin01"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId":"%s","password":"nurse1234!","employeeNo":"E9%d",
                                 "name":"박OO","role":"NURSE","departmentId":%d}"""
                                .formatted(loginId, n, wardDepartmentId())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // 만들자마자는 들어가진다
        mvc.perform(login(loginId)).andExpect(status().isOk());

        long staffId = om.readTree(created).get("id").asLong();
        mvc.perform(patch("/api/v1/staff/" + staffId + "/deactivate")
                        .header("Authorization", bearer("admin01")))
                .andExpect(status().isNoContent());

        // 삭제가 아니라 중지다. 계정은 남아 있고 로그인만 막힌다.
        mvc.perform(login(loginId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-003"));
    }

    // ── 도우미 ──────────────────────────────────────────────

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
            createDepartment(String actor, String code) throws Exception {
        return post("/api/v1/departments")
                .header("Authorization", bearer(actor))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"code":"%s","name":"검사실%s","deptType":"EXAM"}""".formatted(code, code));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
            login(String loginId) {
        return post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"loginId":"%s","password":"nurse1234!"}""".formatted(loginId));
    }

    private Long wardDepartmentId() {
        return staffRepository.findByLoginIdWithDepartment("ward01")
                .orElseThrow().getDepartment().getId();
    }

    private String bearer(String loginId) throws Exception {
        String body = mvc.perform(login(loginId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + om.readTree(body).get("accessToken").asText();
    }
}
