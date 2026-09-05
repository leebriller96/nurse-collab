package com.nursecollab.domain.transfer.dto;

import com.nursecollab.domain.transfer.entity.TransferEvent;
import com.nursecollab.domain.transfer.entity.TransferStatus;

import java.time.OffsetDateTime;

/** 타임라인 한 줄. 전화로는 절대 남지 않는 것이 이것이다. */
public record TransferEventResponse(
        Long id,
        TransferStatus fromStatus,
        TransferStatus toStatus,
        ActorInfo actor,
        OffsetDateTime occurredAt,
        String reason
) {
    public record ActorInfo(Long id, String name, String departmentName) {}

    public static TransferEventResponse from(TransferEvent event) {
        return new TransferEventResponse(
                event.getId(),
                event.getFromStatus(),
                event.getToStatus(),
                new ActorInfo(event.getActor().getId(), event.getActor().getName(),
                        event.getActorDept().getName()),
                event.getOccurredAt(),
                event.getReason());
    }
}
