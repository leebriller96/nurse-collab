package com.nursecollab.domain.workorder.dto;

import com.nursecollab.domain.workorder.entity.WorkOrderEvent;
import com.nursecollab.domain.workorder.entity.OrderStatus;

import java.time.OffsetDateTime;

/** 타임라인 한 줄. 전화로는 절대 남지 않는 것이 이것이다. */
public record WorkOrderEventResponse(
        Long id,
        OrderStatus fromStatus,
        OrderStatus toStatus,
        /** 종류가 부르는 이름. 화면이 상태 이름표를 들지 않게 한다 — 약제 이력에 "진행중" 이 찍히면 안 된다. */
        String toStatusLabel,
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
                event.getRequest().getOrderType().labelOf(event.getToStatus()),
                new ActorInfo(event.getActor().getId(), event.getActor().getName(),
                        event.getActorDept().getName()),
                event.getOccurredAt(),
                event.getReason());
    }
}
