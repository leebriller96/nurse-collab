package com.nursecollab.domain.notification.service;

import com.nursecollab.domain.notification.dto.NotificationResponse;
import com.nursecollab.domain.notification.dto.NotificationsResponse;
import com.nursecollab.domain.notification.entity.NotiType;
import com.nursecollab.domain.notification.entity.Notification;
import com.nursecollab.domain.notification.repository.NotificationRepository;
import com.nursecollab.domain.staff.repository.StaffRepository;
import com.nursecollab.domain.workorder.entity.WorkOrder;
import com.nursecollab.global.common.PageResponse;
import com.nursecollab.global.error.BusinessException;
import com.nursecollab.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final String REF_TYPE = "WORK_ORDER";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final NotificationRepository notificationRepository;
    private final StaffRepository staffRepository;

    /**
     * 그 요청에 손댄 사람에게 알린다. 행위자 본인은 뺀다.
     * 자기가 방금 누른 것이 알림으로 돌아오면 알림함이 쓸모없어진다.
     *
     * 파트 전원에게 보내지 않는다. 반년치 데이터로 세어 보니 하루 7만 5천 건이었고,
     * 남의 환자 알림까지 쌓이는 알림함은 아무도 열지 않는다. 파트 전체가 알아야 할 변화는
     * 실시간 채널이 이미 전한다. 다만 한쪽 파트에 아직 손댄 사람이 없으면 그 파트 전원에게 보낸다 —
     * 담당자가 없으니 누가 집을지 모른다. 새 요청이 수행 파트 전원에게 가는 것이 이 경우다.
     */
    @Transactional
    public void notifyOrder(WorkOrder request, Long actorId,
                               NotiType notiType, String title, String body) {

        List<StaffRepository.Involved> involved = staffRepository.findInvolvedInOrder(request.getId());
        Set<Long> recipients = new LinkedHashSet<>();
        List<Long> untouchedDepartments = new ArrayList<>();

        for (Long departmentId : List.of(request.getFromDepartment().getId(), request.getToDepartment().getId())) {
            List<Long> ours = involved.stream()
                    .filter(i -> i.departmentId().equals(departmentId))
                    .map(StaffRepository.Involved::staffId)
                    .toList();
            if (ours.isEmpty()) {
                untouchedDepartments.add(departmentId);
            } else {
                recipients.addAll(ours);
            }
        }
        if (!untouchedDepartments.isEmpty()) {
            recipients.addAll(staffRepository.findActiveIdsByDepartmentIds(untouchedDepartments));
        }

        List<Notification> notifications = recipients.stream()
                .filter(id -> !id.equals(actorId))
                .map(id -> Notification.of(id, notiType, REF_TYPE, request.getId(), title, body))
                .toList();

        if (!notifications.isEmpty()) {
            notificationRepository.saveAll(notifications);
        }
    }

    /**
     * 302호 / 뇌 MRI / 15:30 예정
     *
     * <b>환자 이름은 넣지 않는다.</b> 알림은 DB 에 그대로 쌓이고 폰 알림창에도 뜬다.
     * 이름을 넣으면 진료정보가 업무 쪽 DB 에 복사되어 남는다.
     * 침대 번호로도 병동에서는 누구인지 안다.
     *
     * 환자가 없는 업무(장비 수리)는 앞의 병실 없이 업무명만 남는다.
     */
    public String describe(WorkOrder request) {
        StringBuilder sb = new StringBuilder();

        var episode = request.getCareEpisode();
        if (episode != null && episode.getRoomNo() != null) {
            sb.append(episode.getRoomNo()).append("호 / ");
        }
        sb.append(request.getServiceItem().getName());

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
