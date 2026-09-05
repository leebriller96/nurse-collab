package com.nursecollab.domain.transfer.service;

import com.nursecollab.domain.staff.entity.Staff;
import com.nursecollab.domain.staff.repository.StaffRepository;
import com.nursecollab.domain.transfer.dto.MessageCreateRequest;
import com.nursecollab.domain.transfer.dto.MessageResponse;
import com.nursecollab.domain.transfer.entity.RequestMessage;
import com.nursecollab.domain.transfer.entity.TransferRequest;
import com.nursecollab.domain.transfer.event.RequestMessageCreatedEvent;
import com.nursecollab.domain.transfer.repository.RequestMessageRepository;
import com.nursecollab.domain.transfer.repository.TransferRequestRepository;
import com.nursecollab.global.error.BusinessException;
import com.nursecollab.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RequestMessageService {

    private final RequestMessageRepository messageRepository;
    private final TransferRequestRepository requestRepository;
    private final StaffRepository staffRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public List<MessageResponse> findAll(Long requestId, Long staffId) {
        loadRelated(requestId, staffId);
        return messageRepository.findAllByRequestId(requestId)
                .stream().map(MessageResponse::from).toList();
    }

    @Transactional
    public MessageResponse create(Long requestId, MessageCreateRequest req, Long staffId) {
        TransferRequest request = loadRelated(requestId, staffId);
        Staff sender = staffRepository.findByIdWithDepartment(staffId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STAFF_NOT_FOUND));

        RequestMessage saved = messageRepository.save(
                RequestMessage.of(request, sender, req.content()));

        eventPublisher.publishEvent(
                new RequestMessageCreatedEvent(requestId, saved.getId(), staffId));

        return MessageResponse.from(saved);
    }

    /** 관여하는 파트만 대화를 읽고 쓸 수 있다 */
    private TransferRequest loadRelated(Long requestId, Long staffId) {
        TransferRequest request = requestRepository.findByIdWithDepartments(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REQUEST_NOT_FOUND));
        Staff staff = staffRepository.findByIdWithDepartment(staffId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STAFF_NOT_FOUND));
        request.resolveActorSide(staff);
        return request;
    }
}
