package com.nursecollab.domain.workorder.event;

/** 응급·긴급 요청이 접수되지 않은 채 기준 시간을 넘겼다. 누른 사람은 없다. */
public record WorkOrderDelayedEvent(
        Long requestId,
        int waitingMinutes
) {}
