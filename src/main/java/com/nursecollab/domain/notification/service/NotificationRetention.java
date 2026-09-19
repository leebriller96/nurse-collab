package com.nursecollab.domain.notification.service;

import com.nursecollab.domain.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * 오래된 알림을 지운다.
 *
 * 알림은 요청 이력의 복사본이다. 무엇이 언제 일어났는지는 work_order_event 에 그대로 남으므로
 * 지워도 잃는 기록이 없다. 두면 알림함 조회와 미읽음 세기가 해마다 느려진다.
 *
 * 안 읽은 알림을 더 오래 두는 것은 휴가에서 돌아온 사람이 볼 수 있게 하려는 것이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationRetention {

    static final int READ_DAYS = 30;
    static final int UNREAD_DAYS = 90;

    private final NotificationRepository notificationRepository;

    /** 감사 로그 파티션(03:30) 다음, 데모 초기화(04:00) 전에 돈다 */
    @Scheduled(cron = "0 40 3 * * *", zone = "Asia/Seoul")
    public void daily() {
        try {
            purge();
        } catch (RuntimeException e) {
            // 못 지워도 알림함은 계속 돈다. 다음 날 다시 한다.
            log.error("오래된 알림 정리에 실패했다", e);
        }
    }

    @Transactional
    public int purge() {
        OffsetDateTime now = OffsetDateTime.now();
        int deleted = notificationRepository.deleteOlderThan(
                now.minusDays(READ_DAYS), now.minusDays(UNREAD_DAYS));
        if (deleted > 0) {
            log.info("오래된 알림 {}건을 지웠다", deleted);
        }
        return deleted;
    }
}
