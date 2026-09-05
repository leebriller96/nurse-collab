package com.nursecollab.domain.transfer.event;

/** 요청 스레드에 새 메시지가 달렸다는 사실 */
public record RequestMessageCreatedEvent(Long requestId, Long messageId, Long senderId) {}
