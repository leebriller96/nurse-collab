package com.nursecollab.domain.episode.service;

import com.nursecollab.domain.department.repository.DepartmentRepository;
import com.nursecollab.domain.episode.dto.ActiveSubjectsRequest;
import com.nursecollab.domain.episode.dto.ActiveSubjectsResponse;
import com.nursecollab.domain.episode.dto.EpisodeRegistrationRequest;
import com.nursecollab.domain.episode.dto.MessageRefsResponse;
import com.nursecollab.domain.episode.dto.MessageRegistrationRequest;
import com.nursecollab.domain.episode.entity.CareEpisode;
import com.nursecollab.domain.episode.repository.CareEpisodeRepository;
import com.nursecollab.domain.staff.entity.StaffRole;
import com.nursecollab.domain.workorder.entity.OrderStatus;
import com.nursecollab.domain.workorder.repository.WorkOrderRepository;
import com.nursecollab.domain.workorder.service.RequestMessageService;
import com.nursecollab.global.error.BusinessException;
import com.nursecollab.global.error.ErrorCode;
import com.nursecollab.global.security.LoginStaff;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 원내의 질문에 답하는 업무 쪽 사실.
 *
 * 같은 프로세스에서 묻든({@link LocalWorkRelationAdapter}) HTTP 로 묻든
 * ({@code /work-relations}) 답은 여기 한 곳에서 나온다. 두 군데에 두면 한쪽만 고쳐지는 날
 * 한 서버일 때와 두 서버일 때 볼 수 있는 환자가 달라진다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkRelationService {

    private final WorkOrderRepository workOrderRepository;
    private final CareEpisodeRepository careEpisodeRepository;
    private final DepartmentRepository departmentRepository;
    private final RequestMessageService requestMessageService;

    public boolean hasActiveOrderTo(UUID subjectRef, Long departmentId) {
        return workOrderRepository.existsActiveBySubjectAndToDepartment(
                subjectRef, departmentId, OrderStatus.terminals());
    }

    public Set<UUID> subjectsWithActiveOrdersTo(Collection<UUID> subjectRefs, Long departmentId) {
        if (subjectRefs == null || subjectRefs.isEmpty()) return Set.of();

        return workOrderRepository.findActiveBySubjectRefs(subjectRefs, OrderStatus.terminals())
                .stream()
                .filter(r -> r.getToDepartment().getId().equals(departmentId))
                .map(r -> r.getCareEpisode().getSubjectRef())
                .collect(Collectors.toSet());
    }

    /**
     * 침대가 찼다.
     *
     * 같은 가명이 다시 오면 아무것도 하지 않는다. 가명은 원내가 새로 만든 무작위 값이라
     * 겹칠 일이 없고, 다시 온 것은 재시도다. 재시도가 안전해야
     * "원내를 먼저 쓰고 업무 쪽은 다시 시도한다" 가 성립한다.
     */
    @Transactional
    public void registerEpisode(UUID subjectRef, Long departmentId, String roomNo, String bedNo,
                                OffsetDateTime admittedAt) {
        if (careEpisodeRepository.existsById(subjectRef)) return;

        if (!departmentRepository.existsById(departmentId)) {
            throw new BusinessException(ErrorCode.MASTER_NOT_FOUND);
        }
        careEpisodeRepository.save(CareEpisode.of(
                subjectRef, departmentRepository.getReferenceById(departmentId),
                roomNo, bedNo, admittedAt));
    }

    /** 메시지가 달렸다. 관여하는 파트인지는 요청 당사자를 아는 대화 서비스가 판정한다. */
    @Transactional
    public void registerMessage(Long orderId, UUID messageRef, Long senderId) {
        requestMessageService.register(orderId, messageRef, senderId);
    }

    public Set<UUID> readableMessageRefs(Long orderId, Long readerId) {
        return requestMessageService.readableRefs(orderId, readerId);
    }

    // ------------------------------------------------------------------
    // HTTP 로 물을 때. 누가 묻는지 확인하는 것이 추가된다.
    // ------------------------------------------------------------------

    /**
     * 자기 파트에 대해서만 묻는다.
     *
     * 원내는 사용자의 토큰을 그대로 실어 온다. 여기서 소속을 대조하지 않으면
     * 토큰 하나로 모든 파트에 무엇이 걸려 있는지 긁어갈 수 있다.
     */
    public ActiveSubjectsResponse activeSubjects(ActiveSubjectsRequest request, LoginStaff loginStaff) {
        if (!request.departmentId().equals(loginStaff.departmentId())) {
            throw new BusinessException(ErrorCode.NOT_RELATED_DEPARTMENT);
        }
        return new ActiveSubjectsResponse(List.copyOf(
                subjectsWithActiveOrdersTo(request.subjectRefs(), request.departmentId())));
    }

    /** 침대 등록은 그 병동 직원이나 관리자만 한다. */
    @Transactional
    public void register(EpisodeRegistrationRequest request, LoginStaff loginStaff) {
        boolean allowed = loginStaff.role() == StaffRole.ADMIN
                || request.departmentId().equals(loginStaff.departmentId());
        if (!allowed) {
            throw new BusinessException(ErrorCode.NOT_RELATED_DEPARTMENT);
        }
        registerEpisode(request.subjectRef(), request.departmentId(),
                request.roomNo(), request.bedNo(), request.admittedAt());
    }

    /** 남의 이름으로 메시지를 달 수 없다. */
    @Transactional
    public void registerMessage(MessageRegistrationRequest request, LoginStaff loginStaff) {
        requireSelf(request.senderId(), loginStaff);
        registerMessage(request.orderId(), request.messageRef(), request.senderId());
    }

    /** 남이 읽을 수 있는 목록을 알아낼 수 없다. */
    public MessageRefsResponse messageRefs(Long orderId, Long readerId, LoginStaff loginStaff) {
        requireSelf(readerId, loginStaff);
        return new MessageRefsResponse(List.copyOf(readableMessageRefs(orderId, readerId)));
    }

    private static void requireSelf(Long staffId, LoginStaff loginStaff) {
        if (!staffId.equals(loginStaff.staffId())) {
            throw new BusinessException(ErrorCode.NOT_RELATED_DEPARTMENT);
        }
    }
}
