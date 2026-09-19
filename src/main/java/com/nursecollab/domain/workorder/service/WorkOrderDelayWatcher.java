package com.nursecollab.domain.workorder.service;

import com.nursecollab.domain.workorder.repository.WorkOrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * 응급·긴급 요청이 접수되지 않은 채 머물러 있는지 매분 본다.
 *
 * 새 요청 알림은 수행 파트 전원에게 이미 갔다. 그래도 아무도 누르지 않은 요청은
 * 큐에 조용히 남는다 — 모두 다른 환자를 보고 있었거나, 폰이 주머니에 있었다.
 * 같은 사람들에게 또 울리지 않고 파트를 움직일 수 있는 사람(수간호사)과 요청자에게 올린다.
 */
@Slf4j
@Component
public class WorkOrderDelayWatcher {

    /** 서버가 오래 꺼져 있다 켜지며 묵은 요청이 한꺼번에 울리지 않게 한다 */
    private static final Duration LOOK_BACK = Duration.ofHours(12);

    private final WorkOrderRepository requestRepository;
    private final WorkOrderDelayService delayService;
    private final Duration emergency;
    private final Duration urgent;

    public WorkOrderDelayWatcher(WorkOrderRepository requestRepository,
                                 WorkOrderDelayService delayService,
                                 @Value("${app.delay-escalation.emergency-minutes:10}") long emergencyMinutes,
                                 @Value("${app.delay-escalation.urgent-minutes:30}") long urgentMinutes) {
        this.requestRepository = requestRepository;
        this.delayService = delayService;
        this.emergency = Duration.ofMinutes(emergencyMinutes);
        this.urgent = Duration.ofMinutes(urgentMinutes);
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void scheduled() {
        try {
            run();
        } catch (RuntimeException e) {
            // 한 번 실패했다고 감시를 멈추지 않는다. 다음 분에 다시 본다.
            log.error("접수 지연 확인에 실패했다", e);
        }
    }

    public void run() {
        OffsetDateTime now = OffsetDateTime.now();
        for (var delayed : requestRepository.findDelayed(
                now.minus(emergency), now.minus(urgent), now.minus(LOOK_BACK))) {
            delayService.markAndPublish(delayed.id(), delayed.requestedAt(), now);
        }
    }
}
