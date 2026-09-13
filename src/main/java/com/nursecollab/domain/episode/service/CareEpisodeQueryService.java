package com.nursecollab.domain.episode.service;

import com.nursecollab.domain.episode.dto.CareEpisodeSummary;
import com.nursecollab.domain.episode.entity.CareEpisode;
import com.nursecollab.domain.episode.repository.CareEpisodeRepository;
import com.nursecollab.domain.staff.entity.StaffRole;
import com.nursecollab.domain.workorder.entity.OrderStatus;
import com.nursecollab.domain.workorder.repository.WorkOrderRepository;
import com.nursecollab.global.error.BusinessException;
import com.nursecollab.global.error.ErrorCode;
import com.nursecollab.global.security.LoginStaff;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 병동의 침대 목록.
 *
 * 예전에는 이 화면이 {@code /encounters} 로 재원 목록을 받았고, 그 응답에 이름과
 * 진단명이 실려 있었다. 그래서 원내망 밖에서도 이름이 그대로 보였다 —
 * 다른 화면은 이름이 사라지는데 이 화면만 아니었다.
 *
 * 이제 여기서는 침대와 요청 건수까지만 나간다. 사람은 화면이 따로 채운다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CareEpisodeQueryService {

    private final CareEpisodeRepository careEpisodeRepository;
    private final WorkOrderRepository workOrderRepository;

    /**
     * 내 파트의 침대 목록. 관리자만 다른 파트를 지정할 수 있다.
     *
     * 페이지를 나누지 않는다. 한 병동의 침대는 수십 개고, 간호사는 근무 시작에
     * 전부 훑는다. 두 페이지로 나뉘면 두 번째 페이지를 안 보게 된다.
     */
    public List<CareEpisodeSummary> findInDepartment(Long departmentId, LoginStaff loginStaff) {
        Long target = resolveTarget(departmentId, loginStaff);

        List<CareEpisode> episodes = careEpisodeRepository.findAdmittedInDepartment(target);
        if (episodes.isEmpty()) return List.of();

        // 카드마다 요청을 따로 세면 침대 수만큼 쿼리가 나간다. 한 번에 가져와 묶는다.
        Map<UUID, Long> requestCount = workOrderRepository
                .findActiveBySubjectRefs(
                        episodes.stream().map(CareEpisode::getSubjectRef).toList(),
                        OrderStatus.terminals())
                .stream()
                .collect(Collectors.groupingBy(r -> r.getCareEpisode().getSubjectRef(),
                        Collectors.counting()));

        return episodes.stream()
                .map(e -> CareEpisodeSummary.of(e,
                        requestCount.getOrDefault(e.getSubjectRef(), 0L).intValue()))
                .toList();
    }

    /**
     * 침대 하나. 병동 화면이 환자 상세를 그릴 때 업무 쪽 절반을 여기서 받는다.
     *
     * 접근 판정은 원내와 같은 규칙을 쓴다 — 담당 병동이거나, 우리 파트로 온
     * 진행중 요청이 이 대상에 걸려 있거나. 여기에는 사람이 없지만
     * "어느 침대에 무슨 요청이 걸려 있는가" 도 아무나 볼 일은 아니다.
     */
    public CareEpisodeSummary.Detail findOne(UUID subjectRef, LoginStaff loginStaff) {
        CareEpisode episode = careEpisodeRepository.findWithDepartment(subjectRef)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENCOUNTER_NOT_FOUND));

        boolean ownWard = loginStaff.role() == StaffRole.ADMIN
                || episode.getDepartment().getId().equals(loginStaff.departmentId());
        if (!ownWard && !workOrderRepository.existsActiveBySubjectAndToDepartment(
                subjectRef, loginStaff.departmentId(), OrderStatus.terminals())) {
            throw new BusinessException(ErrorCode.NOT_RELATED_DEPARTMENT);
        }

        List<CareEpisodeSummary.ActiveRequest> active = workOrderRepository
                .findActiveBySubjectRefs(List.of(subjectRef), OrderStatus.terminals())
                .stream()
                .map(r -> new CareEpisodeSummary.ActiveRequest(
                        r.getId(), r.getRequestNo(), r.getServiceItem().getName(),
                        r.getStatus().name(), r.getOrderType().labelOf(r.getStatus()),
                        r.getScheduledAt()))
                .toList();

        return new CareEpisodeSummary.Detail(
                episode.getSubjectRef(), episode.getRoomNo(), episode.getBedNo(),
                episode.getAdmittedAt(), active);
    }

    /**
     * 남의 병동 침대를 들여다볼 수 있으면 안 된다.
     * 여기에는 이름이 없지만, 어느 병실이 찼는지도 그 병동이 알 일이다.
     */
    private Long resolveTarget(Long departmentId, LoginStaff loginStaff) {
        if (departmentId == null || departmentId.equals(loginStaff.departmentId())) {
            return loginStaff.departmentId();
        }
        if (loginStaff.role() != StaffRole.ADMIN) {
            throw new BusinessException(ErrorCode.NOT_RELATED_DEPARTMENT);
        }
        return departmentId;
    }
}
