package com.nursecollab.domain.workorder.dto;

import com.nursecollab.domain.workorder.entity.WorkOrderEvent;
import com.nursecollab.domain.workorder.entity.OrderStatus;

import java.time.OffsetDateTime;

/** 타임라인 한 줄. 전화로는 절대 남지 않는 것이 이것이다. */
public record WorkOrderEventResponse(
        Long id,
        OrderStatus fromStatus,
        OrderStatus toStatus,
        ActorInfo actor,
        OffsetDateTime occurredAt,
        String reason
) {
    public record ActorInfo(Long id, String name, String departmentName) {}

    public static WorkOrderEventResponse from(WorkOrderEvent event) {
        return new WorkOrderEventResponse(
                event.getId(),
                event.getFromStatus(),
                event.getToStatus(),
                new ActorInfo(event.getActor().getId(), event.getActor().getName(),
                        event.getActorDept().getName()),
                event.getOccurredAt(),
                event.getReason());
    }
}
