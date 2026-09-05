package com.nursecollab.domain.transfer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

/** 상태 전이 요청 */
public record TransitionRequest(
        @NotBlank(message = "변경할 상태는 필수입니다.")
        String toStatus,

        String reason,              // 보류/취소 시 필수 (검증은 도메인에서)
        OffsetDateTime scheduledAt, // 접수 시 필수

        @NotNull(message = "버전 정보는 필수입니다.")
        Long version
) {}
