package com.nursecollab.domain.workorder.event;

/** 요청이 새로 만들어졌다는 사실. 검사실 큐에 새 줄이 생겼다는 뜻이다. */
public record WorkOrderCreatedEvent(Long requestId, Long actorId) {}
