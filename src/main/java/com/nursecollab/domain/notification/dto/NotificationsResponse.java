package com.nursecollab.domain.notification.dto;

import com.nursecollab.global.common.PageResponse;

/** 목록과 미읽음 수를 함께 준다. 뱃지를 위해 두 번 부르지 않게 하려는 것이다. */
public record NotificationsResponse(
        PageResponse<NotificationResponse> page,
        long unreadCount
) {}
