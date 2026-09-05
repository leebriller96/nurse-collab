package com.nursecollab.domain.encounter.service;

import com.nursecollab.domain.encounter.dto.EncounterExamView;
import com.nursecollab.domain.encounter.dto.EncounterFullView;
import com.nursecollab.domain.encounter.dto.EncounterSummary;
import com.nursecollab.domain.encounter.dto.EncounterView;
import com.nursecollab.domain.encounter.entity.Encounter;
import com.nursecollab.domain.encounter.entity.EncounterStatus;
import com.nursecollab.domain.encounter.repository.EncounterRepository;
import com.nursecollab.domain.patient.dto.AlertResponse;
import com.nursecollab.domain.patient.dto.AlertSummary;
import com.nursecollab.domain.patient.dto.ChecklistWarning;
import com.nursecollab.domain.patient.entity.AlertType;
import com.nursecollab.domain.patient.entity.PatientAlert;
import com.nursecollab.domain.patient.repository.PatientAlertRepository;
import com.nursecollab.domain.staff.entity.StaffRole;
import com.nursecollab.domain.transfer.entity.TransferRequest;
import com.nursecollab.domain.transfer.entity.TransferStatus;
import com.nursecollab.domain.transfer.repository.TransferRequestRepository;
import com.nursecollab.global.common.PageResponse;
import com.nursecollab.global.error.BusinessException;
import com.nursecollab.global.error.ErrorCode;
import com.nursecollab.global.security.LoginStaff;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EncounterQueryService {

    private final EncounterRepository encounterRepository;
    private final PatientAlertRepository alertRepository;
    private final TransferRequestRepository transferRequestRepository;

    /**
     * 내 파트의 재원 목록.
     * 관리자만 다른 파트를 지정할 수 있다.
     */
    public PageResponse<EncounterSummary> findAll(Long departmentId, EncounterStatus status,
                                                  String keyword, Pageable pageable,
                                                  LoginStaff loginStaff) {

        Long targetDepartmentId = resolveTargetDepartment(departmentId, loginStaff);
        var page = encounterRepository.search(targetDepartmentId,
                status == null ? EncounterStatus.ADMITTED : status,
                (keyword == null || keyword.isBlank()) ? null : keyword,
                pageable);

        List<Encounter> encounters = page.getContent();
        if (encounters.isEmpty()) {
            return new PageResponse<>(List.of(), page.getNumber(), page.getSize(),
                    page.getTotalElements(), page.getTotalPages());
        }

        // 카드마다 알림/요청을 따로 조회하면 환자 수만큼 쿼리가 나간다. 한 번에 가져와 묶는다.
        List<Long> patientIds = encounters.stream().map(e -> e.getPatient().getId()).toList();
        List<Long> encounterIds = encounters.stream().map(Encounter::getId).toList();

        Map<Long, List<AlertSummary>> alertsByPatient = alertRepository.findActiveByPatientIds(patientIds)
                .stream()
                .collect(Collectors.groupingBy(a -> a.getPatient().getId(),
                        Collectors.mapping(AlertSummary::from, Collectors.toList())));

        Map<Long, Long> requestCountByEncounter = transferRequestRepository
                .findActiveByEncounterIds(encounterIds, TransferStatus.terminals())
                .stream()
                .collect(Collectors.groupingBy(r -> r.getEncounter().getId(), Collectors.counting()));

        return PageResponse.of(page.map(encounter -> EncounterSummary.of(
                encounter,
                alertsByPatient.getOrDefault(encounter.getPatient().getId(), List.of()),
                requestCountByEncounter.getOrDefault(encounter.getId(), 0L).intValue())));
    }

    /**
     * 재원 상세. 이 시스템에서 가장 조심해야 하는 API 다.
     *
     * 접근 판정을 소속이 아니라 관계로 한다.
     * "검사실이니까 볼 수 있다" 가 아니라 "우리 파트로 온 진행중 요청이 있으니까 볼 수 있다" 이다.
     * 요청이 끝나면 접근 권한도 함께 사라진다.
     */
    public EncounterView findDetail(Long encounterId, LoginStaff loginStaff) {

        Encounter encounter = encounterRepository.findByIdWithPatientAndDepartment(encounterId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENCOUNTER_NOT_FOUND));

        List<PatientAlert> alerts = alertRepository.findActiveByPatientId(encounter.getPatient().getId());

        boolean ownWard = encounter.getDepartment().getId().equals(loginStaff.departmentId());
        if (loginStaff.role() == StaffRole.ADMIN || ownWard) {
            return fullView(encounter, alerts);
        }

        List<TransferRequest> relatedRequests = transferRequestRepository
                .findActiveByEncounterAndToDepartment(encounterId, loginStaff.departmentId(),
                        TransferStatus.terminals());

        if (relatedRequests.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_RELATED_DEPARTMENT);
        }
        return examView(encounter, alerts, relatedRequests);
    }

    public List<AlertResponse> findAlerts(Long encounterId, LoginStaff loginStaff) {
        Encounter encounter = loadViewable(encounterId, loginStaff);
        return alertRepository.findActiveByPatientId(encounter.getPatient().getId())
                .stream().map(AlertResponse::from).toList();
    }

    /** 상세와 같은 접근 판정을 거치게 해서 우회 경로를 만들지 않는다 */
    private Encounter loadViewable(Long encounterId, LoginStaff loginStaff) {
        Encounter encounter = encounterRepository.findByIdWithPatientAndDepartment(encounterId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENCOUNTER_NOT_FOUND));

        boolean ownWard = encounter.getDepartment().getId().equals(loginStaff.departmentId());
        if (loginStaff.role() == StaffRole.ADMIN || ownWard) {
            return encounter;
        }
        if (!transferRequestRepository.existsActiveByEncounterAndToDepartment(
                encounterId, loginStaff.departmentId(), TransferStatus.terminals())) {
            throw new BusinessException(ErrorCode.NOT_RELATED_DEPARTMENT);
        }
        return encounter;
    }

    private EncounterFullView fullView(Encounter encounter, List<PatientAlert> alerts) {
        List<EncounterFullView.ActiveRequest> activeRequests = transferRequestRepository
                .findActiveByEncounterIds(List.of(encounter.getId()), TransferStatus.terminals())
                .stream()
                .map(r -> new EncounterFullView.ActiveRequest(
                        r.getId(), r.getRequestNo(), r.getExamType().getName(),
                        r.getStatus().name(), r.getScheduledAt()))
                .toList();

        return EncounterFullView.of(encounter,
                alerts.stream().map(AlertResponse::from).toList(),
                activeRequests);
    }

    private EncounterExamView examView(Encounter encounter, List<PatientAlert> alerts,
                                       List<TransferRequest> relatedRequests) {
        // 우리 파트로 온 요청들의 필수 확인 항목을 모아 환자 주의사항과 교차시킨다
        Collection<AlertType> required = new ArrayList<>();
        relatedRequests.forEach(r -> required.addAll(r.getExamType().requiredAlertTypes()));

        return EncounterExamView.of(encounter,
                alerts.stream().map(AlertResponse::from).toList(),
                ChecklistWarning.cross(required, alerts));
    }

    private Long resolveTargetDepartment(Long requested, LoginStaff loginStaff) {
        if (requested == null || requested.equals(loginStaff.departmentId())) {
            return loginStaff.departmentId();
        }
        if (loginStaff.role() != StaffRole.ADMIN) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_ROLE);
        }
        return requested;
    }
}
