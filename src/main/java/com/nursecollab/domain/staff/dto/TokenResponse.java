package com.nursecollab.domain.staff.dto;

public record TokenResponse(
        String accessToken,
        String refreshToken
) {}
