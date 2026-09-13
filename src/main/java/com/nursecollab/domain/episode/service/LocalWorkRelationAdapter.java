package com.nursecollab.domain.episode.service;

import com.nursecollab.domain.department.repository.DepartmentRepository;
import com.nursecollab.domain.episode.entity.CareEpisode;
import com.nursecollab.domain.episode.repository.CareEpisodeRepository;
import com.nursecollab.domain.phi.port.WorkRelationPort;
import com.nursecollab.domain.workorder.entity.OrderStatus;
import com.nursecollab.domain.workorder.repository.WorkOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 원내의 질문에 같은 프로세스 안에서 답한다.
 *
 * 업무 쪽 패키지에 둔 이유: 답을 아는 것은 업무 쪽이다. 원내는 질문의 모양만 정하고
 * 누가 어떻게 답하는지는 모른다. 두 서버로 갈라지면 원내 쪽에는 이 구현 대신
 * HTTP 로 묻는 구현이 들어가고, 원내 코드는 한 줄도 바뀌지 않는다.
 */
@Component
@RequiredArgsConstructor
public class LocalWorkRelationAdapter implements WorkRelationPort {

    private final WorkOrderRepository workOrderRepository;
    private final CareEpisodeRepository careEpisodeRepository;
    private final DepartmentRepository departmentRepository;

    @Override
    @Transactional(readOnly = true)
    public boolean hasActiveOrderTo(UUID subjectRef, Long departmentId) {
        return workOrderRepository.existsActiveBySubjectAndToDepartment(
                subjectRef, departmentId, OrderStatus.terminals());
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> subjectsWithActiveOrdersTo(Collection<UUID> subjectRefs, Long departmentId) {
        if (subjectRefs == null || subjectRefs.isEmpty()) return Set.of();

        return workOrderRepository.findActiveBySubjectRefs(subjectRefs, OrderStatus.terminals())
                .stream()
                .filter(r -> r.getToDepartment().getId().equals(departmentId))
                .map(r -> r.getCareEpisode().getSubjectRef())
                .collect(Collectors.toSet());
    }

    @Override
    @Transactional
    public void registerEpisode(UUID subjectRef, Long departmentId, String roomNo, String bedNo,
                                OffsetDateTime admittedAt) {
        careEpisodeRepository.save(CareEpisode.of(
                subjectRef, departmentRepository.getReferenceById(departmentId),
                roomNo, bedNo, admittedAt));
    }
}
