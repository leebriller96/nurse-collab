package com.nursecollab.domain.transfer.dto;

import com.nursecollab.domain.transfer.entity.TransferPriority;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

/**
 * 이송 요청 생성.
 * 수행 파트(toDepartmentId)를 받지 않는 이유는 검사 종류가 그것을 결정하기 때문이다.
 */
public record TransferCreateRequest(
        @NotNull(message = "재원 정보는 필수입니다.")
        Long encounterId,

        @NotNull(message = "검사 종류는 필수입니다.")
        Long examTypeId,

        @NotNull(message = "우선순위는 필수입니다.")
        TransferPriority priority,

        OffsetDateTime desiredAt,

        @Size(max = 500, message = "메모는 500자를 넘을 수 없습니다.")
        String note
) {}
