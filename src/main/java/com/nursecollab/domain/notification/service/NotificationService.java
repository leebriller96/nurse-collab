package com.nursecollab.domain.notification.service;

import com.nursecollab.domain.notification.dto.NotificationResponse;
import com.nursecollab.domain.notification.dto.NotificationsResponse;
import com.nursecollab.domain.notification.entity.NotiType;
import com.nursecollab.domain.notification.entity.Notification;
import com.nursecollab.domain.notification.repository.NotificationRepository;
import com.nursecollab.domain.staff.repository.StaffRepository;
import com.nursecollab.domain.transfer.entity.TransferRequest;
import com.nursecollab.global.common.PageResponse;
import com.nursecollab.global.error.BusinessException;
import com.nursecollab.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final String REF_TYPE = "TRANSFER_REQUEST";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final NotificationRepository notificationRepository;
    private final StaffRepository staffRepository;

    /**
     * 요청에 관여하는 양쪽 파트에 알린다. 행위자 본인은 뺀다.
     * 자기가 방금 누른 것이 알림으로 돌아오면 알림함이 쓸모없어진다.
     */
    @Transactional
    public void notifyTransfer(TransferRequest request, Long actorId,
                               NotiType notiType, String title, String body) {

        List<Long> recipients = staffRepository.findActiveIdsByDepartmentIds(
                List.of(request.getFromDepartment().getId(), request.getToDepartment().getId()));

        List<Notification> notifications = recipients.stream()
                .filter(id -> !id.equals(actorId))
                .map(id -> Notification.of(id, notiType, REF_TYPE, request.getId(), title, body))
                .toList();

        if (!notifications.isEmpty()) {
            notificationRepository.saveAll(notifications);
        }
    }

    /** 302호 김OO / 뇌 MRI / 15:30 예정 */
    public String describe(TransferRequest request) {
        StringBuilder sb = new StringBuilder()
                .append(request.getEncounter().getRoomNo()).append("호 ")
                .append(request.getEncounter().getPatient().getName())
                .append(" / ").append(request.getExamType().getName());

        if (request.getScheduledAt() != null) {
            sb.append(" / ").append(request.getScheduledAt().format(TIME)).append(" 예정");
        }
        return sb.toString();
    }

    @Transactional(readOnly = true)
    public NotificationsResponse find(Long staffId, boolean unreadOnly, Pageable pageable) {
        var page = notificationRepository.findForRecipient(staffId, unreadOnly, pageable);
        return new NotificationsResponse(
                PageResponse.of(page.map(NotificationResponse::from)),
                notificationRepository.countUnread(staffId));
    }

    @Transactional
    public void markRead(Long notificationId, Long staffId) {
        Notification notification = notificationRepository
                .findByIdAndRecipientId(notificationId, staffId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));
        notification.markRead();
    }

    @Transactional
    public void markAllRead(Long staffId) {
        notificationRepository.markAllRead(staffId, OffsetDateTime.now());
    }
}
