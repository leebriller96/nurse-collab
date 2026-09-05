package com.nursecollab.domain.staff.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(
        @NotBlank(message = "갱신 토큰은 필수입니다.")
        String refreshToken
) {}
