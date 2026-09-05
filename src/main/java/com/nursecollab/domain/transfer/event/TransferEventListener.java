package com.nursecollab.domain.transfer.event;

import com.nursecollab.domain.staff.entity.Staff;
import com.nursecollab.domain.staff.repository.StaffRepository;
import com.nursecollab.domain.transfer.entity.TransferRequest;
import com.nursecollab.domain.transfer.entity.TransferStatus;
import com.nursecollab.domain.transfer.repository.TransferRequestRepository;
import com.nursecollab.infra.realtime.RealtimeEvent;
import com.nursecollab.infra.realtime.RealtimeNotifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.OffsetDateTime;

/**
 * 실시간 알림 발송.
 *
 * AFTER_COMMIT 으로 지정하는 것이 핵심이다.
 * 트랜잭션이 롤백되면 이 메서드는 아예 실행되지 않는다.
 * 즉 "DB 에는 안 바뀌었는데 알림만 날아가는" 상황이 생기지 않는다.
 *
 * 커밋이 이미 끝난 뒤라서 원래 트랜잭션은 남아 있지 않다.
 * 페이로드에 필요한 값을 읽으려면 새 트랜잭션을 열어야 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransferEventListener {

    private final TransferRequestRepository requestRepository;
    private final StaffRepository staffRepository;
    private final RealtimeNotifier notifier;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onCreated(TransferCreatedEvent event) {
        publish(event.requestId(), event.actorId(),
                RealtimeEvent.EventType.TRANSFER_CREATED, null, TransferStatus.REQUESTED);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onStatusChanged(TransferStatusChangedEvent event) {
        publish(event.requestId(), event.actorId(),
                RealtimeEvent.EventType.TRANSFER_STATUS_CHANGED,
                event.fromStatus(), event.toStatus());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onMessageCreated(RequestMessageCreatedEvent event) {
        publish(event.requestId(), event.senderId(),
                RealtimeEvent.EventType.MESSAGE_CREATED, null, null);
    }

    private void publish(Long requestId, Long actorId, RealtimeEvent.EventType type,
                         TransferStatus fromStatus, TransferStatus toStatus) {
        try {
            TransferRequest request = requestRepository.findDetailById(requestId).orElse(null);
            Staff actor = staffRepository.findByIdWithDepartment(actorId).orElse(null);
            if (request == null || actor == null) {
                log.warn("알림 대상을 찾지 못했다. requestId={}, actorId={}", requestId, actorId);
                return;
            }

            notifier.broadcast(
                    request.getFromDepartment().getId(),
                    request.getToDepartment().getId(),
                    new RealtimeEvent(
                            type,
                            request.getId(),
                            request.getRequestNo(),
                            fromStatus,
                            toStatus,
                            request.getPriority(),
                            request.getEncounter().getPatient().getName(),
                            request.getEncounter().getRoomNo(),
                            request.getExamType().getName(),
                            actor.getName(),
                            actor.getDepartment().getName(),
                            OffsetDateTime.now()));
        } catch (RuntimeException e) {
            // 이미 커밋된 업무 처리를 알림 때문에 되돌릴 수는 없다
            log.warn("실시간 알림 준비 실패. requestId={}", requestId, e);
        }
    }
}
