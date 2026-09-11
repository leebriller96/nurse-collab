package com.nursecollab.infra.realtime;

import com.nursecollab.domain.workorder.entity.OrderPriority;
import com.nursecollab.domain.workorder.entity.OrderStatus;

import java.time.OffsetDateTime;

/**
 * 파트 채널로 나가는 실시간 알림.
 *
 * 화면을 이 내용으로 그리라는 뜻이 아니라 "다시 조회하라" 는 신호다.
 * 알림 자체에 화면을 맡기면 메시지를 놓치거나 순서가 뒤바뀌었을 때 화면이 서버와 어긋난다.
 * 사람이 읽을 수 있는 필드를 함께 싣는 것은 토스트 문구를 만들기 위해서다.
 *
 * actorId 는 자기가 한 일을 자기에게 알리지 않으려고 싣는다. 방송은 파트 채널로 나가므로
 * 누른 사람에게도 되돌아온다. 그대로 두면 확인과 알림이 동시에 뜬다.
 * 이름으로 거르지 않는 것은 동명이인 때문이다.
 */
public record RealtimeEvent(
        EventType eventType,
        Long requestId,
        String requestNo,
        OrderStatus fromStatus,
        OrderStatus toStatus,
        OrderPriority priority,
        String patientName,
        String roomNo,
        String examName,
        Long actorId,
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
