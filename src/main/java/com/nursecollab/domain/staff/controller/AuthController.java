package com.nursecollab.domain.staff.controller;

import com.nursecollab.domain.staff.dto.LoginRequest;
import com.nursecollab.domain.staff.dto.LoginResponse;
import com.nursecollab.domain.staff.dto.RefreshRequest;
import com.nursecollab.domain.staff.dto.StaffResponse;
import com.nursecollab.domain.staff.dto.TokenResponse;
import com.nursecollab.domain.staff.service.AuthService;
import com.nursecollab.global.security.LoginStaff;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal LoginStaff loginStaff) {
        authService.logout(loginStaff.staffId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<StaffResponse> me(@AuthenticationPrincipal LoginStaff loginStaff) {
        return ResponseEntity.ok(authService.me(loginStaff.staffId()));
    }
}
