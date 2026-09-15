package com.nursecollab.domain.workorder.dto;

import com.nursecollab.domain.workorder.entity.OrderPriority;
import com.nursecollab.domain.workorder.entity.WorkOrder;
import com.nursecollab.domain.workorder.entity.OrderStatus;

import java.time.OffsetDateTime;

public record WorkOrderCreateResponse(
        Long id,
        String requestNo,
        OrderStatus status,
        OrderPriority priority,
        OffsetDateTime requestedAt,
        Long version
) {
    public static WorkOrderCreateResponse from(WorkOrder request) {
        return new WorkOrderCreateResponse(
                request.getId(),
                request.getRequestNo(),
                request.getStatus(),
                request.getPriority(),
                request.getRequestedAt(),
                request.getVersion());
    }
}
