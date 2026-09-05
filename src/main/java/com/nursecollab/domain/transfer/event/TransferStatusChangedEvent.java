package com.nursecollab.domain.transfer.event;

import com.nursecollab.domain.transfer.entity.TransferStatus;

/**
 * 상태가 바뀌었다는 사실만 담는다.
 * 알림 발송은 이 이벤트를 받아 커밋 이후에 처리한다 (5단계).
 */
public record TransferStatusChangedEvent(
        Long requestId,
        TransferStatus fromStatus,
        TransferStatus toStatus,
        Long actorId
) {}
