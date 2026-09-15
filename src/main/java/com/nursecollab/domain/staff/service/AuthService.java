package com.nursecollab.domain.staff.service;

import com.nursecollab.domain.staff.dto.LoginRequest;
import com.nursecollab.domain.staff.dto.LoginResponse;
import com.nursecollab.domain.staff.dto.RefreshRequest;
import com.nursecollab.domain.staff.dto.StaffResponse;
import com.nursecollab.domain.staff.dto.TokenResponse;
import com.nursecollab.domain.staff.entity.Staff;
import com.nursecollab.domain.staff.repository.StaffRepository;
import com.nursecollab.global.audit.AuditRecorder;
import com.nursecollab.global.error.BusinessException;
import com.nursecollab.global.error.ErrorCode;
import com.nursecollab.global.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    /** 아이디 칸에 비밀번호를 친 경우 그 흔적이 길게 남지 않게 자른다 */
    private static final int ATTEMPTED_ID_MAX = 50;

    private final StaffRepository staffRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final RefreshTokenStore refreshTokenStore;
    private final AuditRecorder auditRecorder;

    /**
     * 로그인.
     *
     * 실패도 감사 기록에 남긴다. 화면에는 없는 아이디와 틀린 비밀번호를 같은 AUTH-001 로 돌려주지만
     * (계정이 있는지 알려 주지 않기 위해) 기록에는 둘을 구별해 적는다. 조사할 때는 그 차이가 필요하다.
     * 기록은 따로 커밋되므로 예외로 끝나도 남는다.
     */
    @Transactional
    public LoginResponse login(LoginRequest request) {
        Staff staff = staffRepository.findByLoginIdWithDepartment(request.loginId()).orElse(null);
        if (staff == null) {
            audit(null, "LOGIN_FAILED", null, failure(request.loginId(), "UNKNOWN_ID"));
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        // 비활성 여부보다 비밀번호를 먼저 본다.
        // 순서가 반대면 비밀번호를 몰라도 계정 존재 여부를 알아낼 수 있다.
        if (!passwordEncoder.matches(request.password(), staff.getPasswordHash())) {
            audit(staff.getId(), "LOGIN_FAILED", staff.getId(), failure(request.loginId(), "BAD_PASSWORD"));
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (!staff.isActive()) {
            audit(staff.getId(), "LOGIN_FAILED", staff.getId(), failure(request.loginId(), "INACTIVE"));
            throw new BusinessException(ErrorCode.INACTIVE_ACCOUNT);
        }

        staff.recordLogin();

        String accessToken = tokenProvider.createAccessToken(staff);
        String refreshToken = tokenProvider.createRefreshToken(staff);
        refreshTokenStore.save(staff.getId(), refreshToken);

        // 토큰을 다 만든 뒤에 남긴다. 중간에 실패했는데 "로그인했다" 가 남으면 기록을 믿을 수 없다.
        audit(staff.getId(), "LOGIN", staff.getId(), Map.of("loginId", staff.getLoginId()));
        return new LoginResponse(accessToken, refreshToken, StaffResponse.from(staff));
    }

    /**
     * 갱신 토큰으로 새 토큰 쌍을 발급한다.
     * 쓰고 나면 갱신 토큰도 함께 교체(회전)해서, 같은 토큰이 두 번 통하지 않게 한다.
     */
    @Transactional(readOnly = true)
    public TokenResponse refresh(RefreshRequest request) {
        Claims claims;
        try {
            claims = tokenProvider.parse(request.refreshToken());
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
        }

        if (!tokenProvider.isRefreshToken(claims)) {
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
        }

        Long staffId = tokenProvider.staffIdOf(claims);

        // 저장된 것과 다르면 이미 회전됐거나 로그아웃된 토큰이다.
        if (!refreshTokenStore.matches(staffId, request.refreshToken())) {
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
        }

        Staff staff = staffRepository.findByIdWithDepartment(staffId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STAFF_NOT_FOUND));

        if (!staff.isActive()) {
            throw new BusinessException(ErrorCode.INACTIVE_ACCOUNT);
        }

        String accessToken = tokenProvider.createAccessToken(staff);
        String refreshToken = tokenProvider.createRefreshToken(staff);
        refreshTokenStore.save(staffId, refreshToken);

        return new TokenResponse(accessToken, refreshToken);
    }

    public void logout(Long staffId) {
        refreshTokenStore.remove(staffId);
        audit(staffId, "LOGOUT", staffId, null);
    }

    @Transactional(readOnly = true)
    public StaffResponse me(Long staffId) {
        return staffRepository.findByIdWithDepartment(staffId)
                .map(StaffResponse::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.STAFF_NOT_FOUND));
    }

    /** 감사 기록 실패로 로그인을 막지 않는다. 대신 반드시 눈에 띄게 남긴다. */
    private void audit(Long actorId, String action, Long targetId, Map<String, Object> detail) {
        try {
            auditRecorder.recordRequest(actorId, action, "STAFF", targetId, detail);
        } catch (RuntimeException e) {
            log.error("감사 로그 적재 실패. action={}, actorId={}", action, actorId, e);
        }
    }

    private static Map<String, Object> failure(String attemptedLoginId, String reason) {
        String attempted = attemptedLoginId == null ? "" : attemptedLoginId;
        if (attempted.length() > ATTEMPTED_ID_MAX) {
            attempted = attempted.substring(0, ATTEMPTED_ID_MAX);
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("loginId", attempted);
        detail.put("reason", reason);
        return detail;
    }
}
