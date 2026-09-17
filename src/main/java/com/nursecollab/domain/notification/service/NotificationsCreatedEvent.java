package com.nursecollab.domain.notification.service;

import com.nursecollab.domain.notification.entity.NotiType;
import com.nursecollab.domain.workorder.entity.OrderPriority;

import java.util.Set;

/** 알림함에 알림이 남았다. 폰 알림은 이것을 커밋 뒤에 받아 같은 사람에게 같은 문구로 보낸다. */
public record NotificationsCreatedEvent(
        Set<Long> recipients,
        Long orderId,
        NotiType notiType,
        OrderPriority priority,
        String title,
        String body
) {}
