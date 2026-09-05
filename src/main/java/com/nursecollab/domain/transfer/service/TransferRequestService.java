package com.nursecollab.domain.transfer.service;

import com.nursecollab.domain.encounter.entity.Encounter;
import com.nursecollab.domain.encounter.repository.EncounterRepository;
import com.nursecollab.domain.staff.entity.Staff;
import com.nursecollab.domain.staff.repository.StaffRepository;
import com.nursecollab.domain.transfer.dto.TransferCreateRequest;
import com.nursecollab.domain.transfer.dto.TransferCreateResponse;
import com.nursecollab.domain.transfer.dto.TransitionRequest;
import com.nursecollab.domain.transfer.dto.TransitionResponse;
import com.nursecollab.domain.transfer.entity.ActorSide;
import com.nursecollab.domain.transfer.entity.ExamType;
import com.nursecollab.domain.transfer.entity.TransferEvent;
import com.nursecollab.domain.transfer.entity.TransferRequest;
import com.nursecollab.domain.transfer.entity.TransferStatus;
import com.nursecollab.domain.transfer.event.TransferCreatedEvent;
import com.nursecollab.domain.transfer.event.TransferStatusChangedEvent;
import com.nursecollab.domain.transfer.repository.ExamTypeRepository;
import com.nursecollab.domain.transfer.repository.TransferEventRepository;
import com.nursecollab.domain.transfer.repository.TransferRequestRepository;
import com.nursecollab.global.error.BusinessException;
import com.nursecollab.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class TransferRequestService {

    private final TransferRequestRepository requestRepository;
    private final TransferEventRepository eventRepository;
    private final StaffRepository staffRepository;
    private final EncounterRepository encounterRepository;
    private final ExamTypeRepository examTypeRepository;
    private final RequestNoGenerator requestNoGenerator;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 이송 요청 생성.
     * 생성도 하나의 상태 전이(없음 → REQUESTED)로 보고 이력을 함께 남긴다.
     * 그래야 타임라인의 첫 줄이 비지 않고, 대기시간 계산의 기준점이 생긴다.
     */
    @Transactional
    public TransferCreateResponse create(TransferCreateRequest req, Long staffId) {

        Staff requester = staffRepository.findByIdWithDepartment(staffId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STAFF_NOT_FOUND));

        Encounter encounter = encounterRepository.findByIdWithPatientAndDepartment(req.encounterId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ENCOUNTER_NOT_FOUND));

        ExamType examType = examTypeRepository.findByIdWithDepartment(req.examTypeId())
                .orElseThrow(() -> new BusinessException(ErrorCode.EXAM_TYPE_NOT_FOUND));

        // 퇴원 여부와 병동 일치 검증은 엔티티가 한다
        TransferRequest request = TransferRequest.create(
                requestNoGenerator.generate(LocalDate.now()),
                encounter, examType, requester,
                req.priority(), req.desiredAt(), req.note());

        requestRepository.save(request);
        eventRepository.save(TransferEvent.of(
                request, null, TransferStatus.REQUESTED, requester, null));

        eventPublisher.publishEvent(new TransferCreatedEvent(request.getId(), requester.getId()));

        return TransferCreateResponse.from(request);
    }

    /**
     * 상태 전이 처리.
     *
     * 흐름: 조회 → 버전확인 → 행위자판정 → 상태변경(엔티티가 검증) → 이력적재 → 이벤트발행
     * 알림 발송은 여기서 직접 하지 않고 도메인 이벤트로 넘긴다.
     * 트랜잭션이 롤백됐는데 알림만 나가는 사고를 막기 위해서다.
     */
    @Transactional
    public TransitionResponse transition(Long requestId, TransitionRequest req, Long staffId) {

        TransferRequest request = requestRepository.findByIdWithDepartments(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REQUEST_NOT_FOUND));

        Staff actor = staffRepository.findByIdWithDepartment(staffId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STAFF_NOT_FOUND));

        // 클라이언트가 보고 있던 버전과 현재 버전이 같은지 먼저 확인한다.
        // JPA @Version 도 flush 시점에 걸러주지만, 여기서 막아야 사용자에게 친절한 메시지를 줄 수 있다.
        if (!request.getVersion().equals(req.version())) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }

        ActorSide side = request.resolveActorSide(actor);
        TransferStatus fromStatus = request.getStatus();
        TransferStatus toStatus = parseStatus(req.toStatus());

        // 검증과 상태 변경은 엔티티가 스스로 한다 (서비스에 if 문을 쌓지 않는다)
        request.transitionTo(toStatus, side, req.reason(), req.scheduledAt());

        eventRepository.save(TransferEvent.of(request, fromStatus, toStatus, actor, req.reason()));

        eventPublisher.publishEvent(new TransferStatusChangedEvent(
                request.getId(), fromStatus, toStatus, actor.getId()));

        // @Version 은 flush 시점에 올라간다. 여기서 밀어내지 않으면 아직 증가하지 않은 버전이
        // 응답에 실리고, 클라이언트는 그 값으로 다음 요청을 보내 매번 409 를 맞는다.
        requestRepository.flush();

        return TransitionResponse.of(request, actor);
    }

    private TransferStatus parseStatus(String value) {
        try {
            return TransferStatus.from(value);
        } catch (IllegalArgumentException e) {
            // 규칙표에 없는 이름이 오면 "허용되지 않는 전이" 와 같은 취급을 한다
            throw new BusinessException(ErrorCode.INVALID_TRANSITION);
        }
    }
}
