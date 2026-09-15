package com.nursecollab.global.security;

import com.nursecollab.domain.department.entity.Department;
import com.nursecollab.domain.department.entity.DeptType;
import com.nursecollab.domain.staff.entity.Staff;
import com.nursecollab.domain.staff.entity.StaffRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.security.SignatureException;
import com.nursecollab.support.TestKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    /** 단위 테스트는 저장소에 든 개발용 키쌍을 그대로 쓴다. 운영에서는 JwtKeyGuard 가 막는다. */
    private static JwtProperties devKeys(Duration accessValidity) {
        return new JwtProperties(
                TestKeys.devPrivate(), TestKeys.devPublic(),
                accessValidity, Duration.ofDays(14));
    }

    private JwtTokenProvider tokenProvider;
    private Staff staff;

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider(devKeys(Duration.ofMinutes(30)));
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
        JwtTokenProvider attacker = new JwtTokenProvider(otherKeyPair());
        String forged = attacker.createAccessToken(staff);

        assertThatThrownBy(() -> tokenProvider.parse(forged))
                .isInstanceOf(SignatureException.class);
    }

    @Test
    void 공개키만_가진_역할은_토큰을_발급하지_못한다() {
        // 원내 게이트웨이가 이 상태다. 검증은 하되 스스로 관리자 토큰을 만들 수는 없다.
        JwtTokenProvider verifyOnly = new JwtTokenProvider(new JwtProperties(
                null, TestKeys.devPublic(), Duration.ofMinutes(30), Duration.ofDays(14)));

        assertThat(verifyOnly.canIssue()).isFalse();
        assertThatThrownBy(() -> verifyOnly.createAccessToken(staff))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("개인키가 없습니다");

        // 발급은 못 해도 검증은 된다. 그것이 이 역할의 존재 이유다.
        String issued = tokenProvider.createAccessToken(staff);
        assertThat(verifyOnly.toLoginStaff(verifyOnly.parse(issued)).staffId()).isEqualTo(12L);
    }

    @Test
    void 만료된_토큰은_거부된다() {
        JwtTokenProvider expiring = new JwtTokenProvider(devKeys(Duration.ofSeconds(-1)));
        String expired = expiring.createAccessToken(staff);

        assertThatThrownBy(() -> tokenProvider.parse(expired))
                .isInstanceOf(ExpiredJwtException.class);
    }

    /** 공격자가 자기 키로 서명한 상황을 만든다 */
    private static JwtProperties otherKeyPair() {
        var pair = TestKeys.freshPair();
        return new JwtProperties(pair[0], pair[1], Duration.ofMinutes(30), Duration.ofDays(14));
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
