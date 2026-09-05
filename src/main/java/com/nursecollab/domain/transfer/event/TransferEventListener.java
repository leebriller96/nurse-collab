package com.nursecollab.domain.transfer.event;

import com.nursecollab.domain.notification.entity.NotiType;
import com.nursecollab.domain.notification.service.NotificationService;
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
 * 실시간 알림 발송과 알림함 적재.
 *
 * AFTER_COMMIT 으로 지정하는 것이 핵심이다.
 * 트랜잭션이 롤백되면 이 메서드는 아예 실행되지 않는다.
 * 즉 "DB 에는 안 바뀌었는데 알림만 날아가는" 상황이 생기지 않는다.
 *
 * 커밋이 이미 끝난 뒤라서 원래 트랜잭션은 남아 있지 않다.
 * 페이로드에 필요한 값을 읽으려면 새 트랜잭션을 열어야 한다.
 *
 * 실시간 알림과 알림함은 역할이 다르다.
 * 실시간은 지금 화면을 보고 있는 사람에게 닿고, 알림함은 나중에 확인하는 곳이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransferEventListener {

    private final TransferRequestRepository requestRepository;
    private final StaffRepository staffRepository;
    private final RealtimeNotifier notifier;
    private final NotificationService notificationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onCreated(TransferCreatedEvent event) {
        publish(event.requestId(), event.actorId(),
                RealtimeEvent.EventType.TRANSFER_CREATED, null, TransferStatus.REQUESTED);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onStatusChanged(TransferStatusChangedEvent event) {
        publish(event.requestId(), event.actorId(),
                RealtimeEvent.EventType.TRANSFER_STATUS_CHANGED,
                event.fromStatus(), event.toStatus());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
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

            notificationService.notifyTransfer(request, actorId,
                    notiTypeOf(type), titleOf(type, toStatus, actor),
                    notificationService.describe(request));

        } catch (RuntimeException e) {
            // 이미 커밋된 업무 처리를 알림 때문에 되돌릴 수는 없다
            log.warn("알림 처리 실패. requestId={}", requestId, e);
        }
    }

    private NotiType notiTypeOf(RealtimeEvent.EventType type) {
        return switch (type) {
            case TRANSFER_CREATED -> NotiType.TRANSFER_REQUESTED;
            case TRANSFER_STATUS_CHANGED -> NotiType.STATUS_CHANGED;
            case MESSAGE_CREATED -> NotiType.MESSAGE;
        };
    }

    /** 알림함에서 목록만 훑어도 무슨 일인지 알 수 있게 적는다. */
    private String titleOf(RealtimeEvent.EventType type, TransferStatus toStatus, Staff actor) {
        String who = actor.getDepartment().getName();
        return switch (type) {
            case TRANSFER_CREATED -> who + "에서 새 요청을 보냈습니다";
            case MESSAGE_CREATED -> who + " " + actor.getName() + "님이 메시지를 남겼습니다";
            case TRANSFER_STATUS_CHANGED -> who + "에서 " + toStatus.getLabel() + " 처리했습니다";
        };
    }
}
