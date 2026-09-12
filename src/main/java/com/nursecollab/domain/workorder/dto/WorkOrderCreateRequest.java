package com.nursecollab.domain.workorder.dto;

import com.nursecollab.domain.workorder.entity.OrderPriority;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

/**
 * 업무 요청 생성.
 * 수행 파트(toDepartmentId)를 받지 않는 이유는 업무 항목이 그것을 결정하기 때문이다.
 */
public record WorkOrderCreateRequest(
        /**
         * 대상 재원 건. 장비 수리처럼 환자가 없는 업무에서는 비운다.
         * 필수 여부를 여기서 정하지 않는 이유는 업무 종류가 그것을 정하기 때문이다.
         * 검증은 엔티티가 하고, 비어야 할 자리에 값이 오면 ORD-007 로 막는다.
         */
        Long encounterId,

        @NotNull(message = "업무 항목은 필수입니다.")
        Long serviceItemId,

        @NotNull(message = "우선순위는 필수입니다.")
        OrderPriority priority,

        OffsetDateTime desiredAt,

        @Size(max = 500, message = "메모는 500자를 넘을 수 없습니다.")
        String note
) {}
