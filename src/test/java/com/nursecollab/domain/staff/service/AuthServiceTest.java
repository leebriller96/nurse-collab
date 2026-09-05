package com.nursecollab.domain.staff.service;

import com.nursecollab.domain.department.entity.Department;
import com.nursecollab.domain.department.entity.DeptType;
import com.nursecollab.domain.staff.dto.LoginRequest;
import com.nursecollab.domain.staff.dto.LoginResponse;
import com.nursecollab.domain.staff.dto.RefreshRequest;
import com.nursecollab.domain.staff.entity.Staff;
import com.nursecollab.domain.staff.entity.StaffRole;
import com.nursecollab.domain.staff.repository.StaffRepository;
import com.nursecollab.global.error.BusinessException;
import com.nursecollab.global.error.ErrorCode;
import com.nursecollab.global.security.JwtProperties;
import com.nursecollab.global.security.JwtTokenProvider;
import com.nursecollab.global.security.RefreshTokenStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String RAW_PASSWORD = "nurse1234!";

    @Mock
    private StaffRepository staffRepository;

    @Mock
    private RefreshTokenStore refreshTokenStore;

    private PasswordEncoder passwordEncoder;
    private JwtTokenProvider tokenProvider;
    private AuthService authService;

    private Staff staff;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        tokenProvider = new JwtTokenProvider(new JwtProperties(
                "test-secret-key-for-unit-test-0123456789-abcdefgh",
                Duration.ofMinutes(30), Duration.ofDays(14)));
        authService = new AuthService(staffRepository, passwordEncoder,
                tokenProvider, refreshTokenStore);

        staff = staff(passwordEncoder.encode(RAW_PASSWORD));
    }

    @Test
    void 로그인에_성공하면_토큰과_소속_파트가_함께_내려온다() {
        given(staffRepository.findByLoginIdWithDepartment("ward01"))
                .willReturn(Optional.of(staff));

        LoginResponse response = authService.login(new LoginRequest("ward01", RAW_PASSWORD));

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.staff().name()).isEqualTo("김간호");
        assertThat(response.staff().department().deptType()).isEqualTo(DeptType.WARD);
        verify(refreshTokenStore).save(12L, response.refreshToken());
    }

    @Test
    void 로그인하면_마지막_로그인_시각이_기록된다() {
        given(staffRepository.findByLoginIdWithDepartment("ward01"))
                .willReturn(Optional.of(staff));
        assertThat(staff.getLastLoginAt()).isNull();

        authService.login(new LoginRequest("ward01", RAW_PASSWORD));

        assertThat(staff.getLastLoginAt()).isNotNull();
    }

    @Test
    void 없는_아이디와_틀린_비밀번호는_같은_오류를_낸다() {
        given(staffRepository.findByLoginIdWithDepartment("nobody"))
                .willReturn(Optional.empty());
        given(staffRepository.findByLoginIdWithDepartment("ward01"))
                .willReturn(Optional.of(staff));

        ErrorCode noSuchId = catchErrorCode(() ->
                authService.login(new LoginRequest("nobody", RAW_PASSWORD)));
        ErrorCode wrongPassword = catchErrorCode(() ->
                authService.login(new LoginRequest("ward01", "wrong-password")));

        assertThat(noSuchId).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
        assertThat(wrongPassword).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    void 비활성_계정은_비밀번호가_맞아도_로그인할_수_없다() {
        ReflectionTestUtils.setField(staff, "active", false);
        given(staffRepository.findByLoginIdWithDepartment("ward01"))
                .willReturn(Optional.of(staff));

        assertThat(catchErrorCode(() -> authService.login(new LoginRequest("ward01", RAW_PASSWORD))))
                .isEqualTo(ErrorCode.INACTIVE_ACCOUNT);
        verify(refreshTokenStore, never()).save(anyLong(), anyString());
    }

    @Test
    void 접근_토큰으로는_갱신할_수_없다() {
        String accessToken = tokenProvider.createAccessToken(staff);

        assertThat(catchErrorCode(() -> authService.refresh(new RefreshRequest(accessToken))))
                .isEqualTo(ErrorCode.TOKEN_EXPIRED);
    }

    @Test
    void 저장된_것과_다른_갱신_토큰은_거부된다() {
        String refreshToken = tokenProvider.createRefreshToken(staff);
        given(refreshTokenStore.matches(12L, refreshToken)).willReturn(false);

        assertThat(catchErrorCode(() -> authService.refresh(new RefreshRequest(refreshToken))))
                .isEqualTo(ErrorCode.TOKEN_EXPIRED);
    }

    @Test
    void 갱신에_성공하면_갱신_토큰도_함께_교체된다() {
        String oldRefreshToken = tokenProvider.createRefreshToken(staff);
        given(refreshTokenStore.matches(12L, oldRefreshToken)).willReturn(true);
        given(staffRepository.findByIdWithDepartment(12L)).willReturn(Optional.of(staff));

        var response = authService.refresh(new RefreshRequest(oldRefreshToken));

        assertThat(response.accessToken()).isNotBlank();
        verify(refreshTokenStore).save(12L, response.refreshToken());
    }

    @Test
    void 위조된_갱신_토큰은_거부된다() {
        assertThat(catchErrorCode(() -> authService.refresh(new RefreshRequest("not-a-jwt"))))
                .isEqualTo(ErrorCode.TOKEN_EXPIRED);
        verify(refreshTokenStore, never()).matches(anyLong(), anyString());
    }

    @Test
    void 로그아웃하면_저장된_갱신_토큰이_지워진다() {
        authService.logout(12L);

        verify(refreshTokenStore).remove(12L);
    }

    private ErrorCode catchErrorCode(Runnable action) {
        try {
            action.run();
        } catch (BusinessException e) {
            return e.getErrorCode();
        }
        throw new AssertionError("BusinessException 이 발생하지 않았습니다.");
    }

    private Staff staff(String passwordHash) {
        Department department = Department.create("W03", "3병동", DeptType.WARD, "본관 3층", "1303");
        ReflectionTestUtils.setField(department, "id", 3L);

        Staff created = Staff.create("ward01", passwordHash, "E10002", "김간호",
                StaffRole.NURSE, department, "1302");
        ReflectionTestUtils.setField(created, "id", 12L);
        return created;
    }
}
