package com.nursecollab.domain.staff.service;

import com.nursecollab.domain.staff.dto.LoginRequest;
import com.nursecollab.domain.staff.dto.LoginResponse;
import com.nursecollab.domain.staff.dto.RefreshRequest;
import com.nursecollab.domain.staff.dto.StaffResponse;
import com.nursecollab.domain.staff.dto.TokenResponse;
import com.nursecollab.domain.staff.entity.Staff;
import com.nursecollab.domain.staff.repository.StaffRepository;
import com.nursecollab.global.error.BusinessException;
import com.nursecollab.global.error.ErrorCode;
import com.nursecollab.global.security.JwtTokenProvider;
import com.nursecollab.global.security.RefreshTokenStore;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final StaffRepository staffRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final RefreshTokenStore refreshTokenStore;

    @Transactional
    public LoginResponse login(LoginRequest request) {
        Staff staff = staffRepository.findByLoginIdWithDepartment(request.loginId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        // 비활성 여부보다 비밀번호를 먼저 본다.
        // 순서가 반대면 비밀번호를 몰라도 계정 존재 여부를 알아낼 수 있다.
        if (!passwordEncoder.matches(request.password(), staff.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (!staff.isActive()) {
            throw new BusinessException(ErrorCode.INACTIVE_ACCOUNT);
        }

        staff.recordLogin();

        String accessToken = tokenProvider.createAccessToken(staff);
        String refreshToken = tokenProvider.createRefreshToken(staff);
        refreshTokenStore.save(staff.getId(), refreshToken);

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
    }

    @Transactional(readOnly = true)
    public StaffResponse me(Long staffId) {
        return staffRepository.findByIdWithDepartment(staffId)
                .map(StaffResponse::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.STAFF_NOT_FOUND));
    }
}
