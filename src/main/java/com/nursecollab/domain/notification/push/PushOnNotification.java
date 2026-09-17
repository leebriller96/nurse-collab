package com.nursecollab.domain.notification.push;

import com.nursecollab.domain.notification.entity.NotiType;
import com.nursecollab.domain.notification.service.NotificationsCreatedEvent;
import com.nursecollab.domain.workorder.entity.OrderPriority;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 알림함에 남은 알림을 폰으로도 보낸다.
 *
 * 받는 사람과 문구를 따로 정하지 않는다. 알림함 규칙이 이미 골랐고, 두 곳에 규칙이 있으면
 * 폰에는 떴는데 알림함에는 없는 알림이 생긴다. 커밋 뒤에만 보낸다 — 알림함이 롤백됐는데 폰은 울리면 안 된다.
 */
@Component
@RequiredArgsConstructor
class PushOnNotification {

    private final PushService pushService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(NotificationsCreatedEvent event) {
        boolean urgent = event.notiType() == NotiType.DELAYED || event.priority() == OrderPriority.EMERGENCY;
        pushService.deliverAsync(event.recipients(), event.title(), event.body(),
                "/orders/" + event.orderId(), "order-" + event.orderId(), urgent);
    }
}
