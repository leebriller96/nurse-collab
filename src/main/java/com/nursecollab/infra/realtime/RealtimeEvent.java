package com.nursecollab.infra.realtime;

import com.nursecollab.domain.transfer.entity.TransferPriority;
import com.nursecollab.domain.transfer.entity.TransferStatus;

import java.time.OffsetDateTime;

/**
 * 파트 채널로 나가는 실시간 알림.
 *
 * 화면을 이 내용으로 그리라는 뜻이 아니라 "다시 조회하라" 는 신호다.
 * 알림 자체에 화면을 맡기면 메시지를 놓치거나 순서가 뒤바뀌었을 때 화면이 서버와 어긋난다.
 * 사람이 읽을 수 있는 필드를 함께 싣는 것은 토스트 문구를 만들기 위해서다.
 */
public record RealtimeEvent(
        EventType eventType,
        Long requestId,
        String requestNo,
        TransferStatus fromStatus,
        TransferStatus toStatus,
        TransferPriority priority,
        String patientName,
        String roomNo,
        String examName,
        String actorName,
        String actorDepartmentName,
        OffsetDateTime occurredAt
) {
    public enum EventType {
        TRANSFER_CREATED,
        TRANSFER_STATUS_CHANGED,
        MESSAGE_CREATED
    }
}
