package com.nursecollab.domain.staff.dto;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        StaffResponse staff
) {}
