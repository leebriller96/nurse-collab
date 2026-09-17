package com.nursecollab.domain.workorder.service;

import com.nursecollab.domain.workorder.event.WorkOrderDelayedEvent;
import com.nursecollab.domain.workorder.repository.WorkOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * 접수 지연 한 건을 찍고 알림 이벤트를 낸다.
 *
 * 한 건마다 트랜잭션을 따로 연다. 알림은 커밋 이후에만 나가므로(AFTER_COMMIT)
 * 여러 건을 한 트랜잭션에 묶으면 한 건이 실패할 때 앞서 찍은 것까지 알림 없이 되돌아간다.
 */
@Service
@RequiredArgsConstructor
public class WorkOrderDelayService {

    private final WorkOrderRepository requestRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public boolean markAndPublish(Long requestId, OffsetDateTime requestedAt, OffsetDateTime now) {
        if (requestRepository.markDelayNotified(requestId, now) != 1) {
            // 그사이 접수됐거나 다른 서버가 먼저 알렸다
            return false;
        }
        int minutes = (int) Duration.between(requestedAt, now).toMinutes();
        eventPublisher.publishEvent(new WorkOrderDelayedEvent(requestId, minutes));
        return true;
    }
}
