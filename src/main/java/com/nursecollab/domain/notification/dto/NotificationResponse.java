package com.nursecollab.domain.notification.dto;

import com.nursecollab.domain.notification.entity.NotiType;
import com.nursecollab.domain.notification.entity.Notification;

import java.time.OffsetDateTime;

public record NotificationResponse(
        Long id,
        NotiType notiType,
        String refType,
        Long refId,
        String title,
        String body,
        OffsetDateTime readAt,
        OffsetDateTime createdAt
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.getId(), n.getNotiType(), n.getRefType(), n.getRefId(),
                n.getTitle(), n.getBody(), n.getReadAt(), n.getCreatedAt());
    }
}
