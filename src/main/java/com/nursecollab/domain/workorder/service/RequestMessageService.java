package com.nursecollab.domain.workorder.service;

import com.nursecollab.domain.staff.entity.Staff;
import com.nursecollab.domain.staff.repository.StaffRepository;
import com.nursecollab.domain.workorder.dto.MessageResponse;
import com.nursecollab.domain.workorder.entity.RequestMessage;
import com.nursecollab.domain.workorder.entity.WorkOrder;
import com.nursecollab.domain.workorder.event.RequestMessageCreatedEvent;
import com.nursecollab.domain.workorder.repository.RequestMessageRepository;
import com.nursecollab.domain.workorder.repository.WorkOrderRepository;
import com.nursecollab.global.error.BusinessException;
import com.nursecollab.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 요청 대화의 누가·언제.
 *
 * 내용은 원내가 들고 있다. 화면은 원내로 메시지를 보내고, 원내가 본문을 저장한 뒤
 * 여기에 알려 온다({@link #register}). 관여하는 파트인지 판정하는 곳은 여기 한 곳이다.
 */
@Service
@RequiredArgsConstructor
public class RequestMessageService {

    private final RequestMessageRepository messageRepository;
    private final WorkOrderRepository requestRepository;
    private final StaffRepository staffRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public List<MessageResponse> findAll(Long requestId, Long staffId) {
        loadRelated(requestId, staffId);
        return messageRepository.findAllByRequestId(requestId)
                .stream().map(MessageResponse::from).toList();
    }

    /**
     * 원내가 본문을 저장하고 알려 온다.
     *
     * 관여하지 않는 파트면 여기서 거절되고, 원내는 그 예외를 받아 본문 저장을 되돌린다.
     * 같은 열쇠가 다시 오면 아무것도 하지 않는다 — 응답 직전에 끊겨 원내가 다시 보낸 것이다.
     * 실시간 알림과 알림함은 커밋 이후 리스너가 낸다. 내용은 싣지 않는다(여기 없다).
     */
    @Transactional
    public void register(Long requestId, UUID messageRef, Long senderId) {
        WorkOrder request = loadRelated(requestId, senderId);
        if (messageRepository.existsByMessageRef(messageRef)) return;

        Staff sender = staffRepository.findByIdWithDepartment(senderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STAFF_NOT_FOUND));
        RequestMessage saved = messageRepository.save(RequestMessage.of(request, sender, messageRef));

        eventPublisher.publishEvent(
                new RequestMessageCreatedEvent(requestId, saved.getId(), senderId));
    }

    /** 이 사람이 이 요청에서 읽을 수 있는 메시지. 원내는 이 목록의 본문만 내준다. */
    @Transactional(readOnly = true)
    public Set<UUID> readableRefs(Long requestId, Long readerId) {
        loadRelated(requestId, readerId);
        return messageRepository.findAllByRequestId(requestId).stream()
                .map(RequestMessage::getMessageRef)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** 관여하는 파트만 대화를 읽고 쓸 수 있다 */
    private WorkOrder loadRelated(Long requestId, Long staffId) {
        WorkOrder request = requestRepository.findByIdWithDepartments(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REQUEST_NOT_FOUND));
        Staff staff = staffRepository.findByIdWithDepartment(staffId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STAFF_NOT_FOUND));
        request.resolveActorSide(staff);
        return request;
    }
}
