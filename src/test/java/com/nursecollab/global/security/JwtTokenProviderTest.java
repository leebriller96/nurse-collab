package com.nursecollab.global.security;

import com.nursecollab.domain.department.entity.Department;
import com.nursecollab.domain.department.entity.DeptType;
import com.nursecollab.domain.staff.entity.Staff;
import com.nursecollab.domain.staff.entity.StaffRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private static final String SECRET = "test-secret-key-for-unit-test-0123456789-abcdefgh";

    private JwtTokenProvider tokenProvider;
    private Staff staff;

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider(
                new JwtProperties(SECRET, Duration.ofMinutes(30), Duration.ofDays(14)));
        staff = staff(12L, 3L);
    }

    @Test
    void 접근_토큰에는_소속_파트_정보가_함께_담긴다() {
        String token = tokenProvider.createAccessToken(staff);

        LoginStaff loginStaff = tokenProvider.toLoginStaff(tokenProvider.parse(token));

        assertThat(loginStaff.staffId()).isEqualTo(12L);
        assertThat(loginStaff.loginId()).isEqualTo("ward01");
        assertThat(loginStaff.name()).isEqualTo("김간호");
        assertThat(loginStaff.role()).isEqualTo(StaffRole.NURSE);
        assertThat(loginStaff.departmentId()).isEqualTo(3L);
        assertThat(loginStaff.deptType()).isEqualTo(DeptType.WARD);
    }

    @Test
    void 접근_토큰과_갱신_토큰은_서로_구분된다() {
        Claims access = tokenProvider.parse(tokenProvider.createAccessToken(staff));
        Claims refresh = tokenProvider.parse(tokenProvider.createRefreshToken(staff));

        assertThat(tokenProvider.isAccessToken(access)).isTrue();
        assertThat(tokenProvider.isRefreshToken(access)).isFalse();

        assertThat(tokenProvider.isRefreshToken(refresh)).isTrue();
        assertThat(tokenProvider.isAccessToken(refresh)).isFalse();
    }

    @Test
    void 갱신_토큰에는_소속_정보를_담지_않는다() {
        Claims refresh = tokenProvider.parse(tokenProvider.createRefreshToken(staff));

        assertThat(tokenProvider.staffIdOf(refresh)).isEqualTo(12L);
        assertThat(refresh.get("deptId")).isNull();
        assertThat(refresh.get("role")).isNull();
    }

    @Test
    void 다른_키로_서명된_토큰은_거부된다() {
        JwtTokenProvider attacker = new JwtTokenProvider(
                new JwtProperties("another-secret-key-0123456789-abcdefghijklmnop",
                        Duration.ofMinutes(30), Duration.ofDays(14)));
        String forged = attacker.createAccessToken(staff);

        assertThatThrownBy(() -> tokenProvider.parse(forged))
                .isInstanceOf(SignatureException.class);
    }

    @Test
    void 만료된_토큰은_거부된다() {
        JwtTokenProvider expiring = new JwtTokenProvider(
                new JwtProperties(SECRET, Duration.ofSeconds(-1), Duration.ofDays(14)));
        String expired = expiring.createAccessToken(staff);

        assertThatThrownBy(() -> tokenProvider.parse(expired))
                .isInstanceOf(ExpiredJwtException.class);
    }

    private Staff staff(Long staffId, Long departmentId) {
        Department department = Department.create("W03", "3병동", DeptType.WARD, "본관 3층", "1303");
        ReflectionTestUtils.setField(department, "id", departmentId);

        Staff created = Staff.create("ward01", "hash", "E10002", "김간호",
                StaffRole.NURSE, department, "1302");
        ReflectionTestUtils.setField(created, "id", staffId);
        return created;
    }
}
