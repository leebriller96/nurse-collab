package com.nursecollab.domain.phi.service;

import com.nursecollab.domain.encounter.entity.Encounter;
import com.nursecollab.domain.encounter.repository.EncounterRepository;
import com.nursecollab.domain.patient.dto.ChecklistWarning;
import com.nursecollab.domain.patient.entity.AlertType;
import com.nursecollab.domain.patient.entity.PatientAlert;
import com.nursecollab.domain.patient.repository.PatientAlertRepository;
import com.nursecollab.domain.phi.dto.SubjectBrief;
import com.nursecollab.domain.phi.dto.SubjectPhi;
import com.nursecollab.domain.staff.entity.StaffRole;
import com.nursecollab.domain.workorder.entity.OrderStatus;
import com.nursecollab.domain.workorder.repository.WorkOrderRepository;
import com.nursecollab.global.audit.AuditLog;
import com.nursecollab.global.audit.AuditRecorder;
import jakarta.servlet.http.HttpServletRequest;
import com.nursecollab.global.error.BusinessException;
import com.nursecollab.global.error.ErrorCode;
import com.nursecollab.global.security.LoginStaff;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 가명을 사람으로 되돌린다.
 *
 * 업무 응답에는 이름도 진단명도 없다. 화면이 가명을 들고 여기로 다시 물어 채운다.
 * 합치는 곳이 서버가 아니라 브라우저인 이유는 docs/06-hospital-scale.md 2장에 있다.
 *
 * <p><b>접근 판정은 예전과 같다.</b> "소속이 검사실이니까" 가 아니라
 * "우리 파트로 온 진행중 요청이 이 대상에 걸려 있으니까" 로 본다.
 * 판정을 이쪽에 다시 구현하지 않고 업무 쪽 사실을 그대로 물어본다.
 * 두 군데에 두면 한쪽만 고쳐지는 날이 오고, 그때 남의 병동 환자가 열린다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubjectPhiService {

    private final EncounterRepository encounterRepository;
    private final PatientAlertRepository alertRepository;
    private final WorkOrderRepository workOrderRepository;
    private final AuditRecorder auditRecorder;

    /**
     * 한 사람을 연다. 누가 언제 열었는지 감사 로그에 남는다.
     *
     * 여기만 {@code @Audited} 를 쓰지 않는다. 그 AOP 는 대상 식별자를 {@code Long} 으로
     * 꺼내는데 이 조회의 열쇠는 UUID 다. 억지로 맞추면 target_id 가 늘 비고,
     * 감사 로그에서 제일 중요한 "누구를 열었나" 가 사라진다.
     * 그래서 환자 id 를 직접 넣어 기록한다.
     */
    public SubjectPhi findOne(UUID subjectRef, LoginStaff loginStaff) {
        Encounter encounter = requireViewable(subjectRef, loginStaff);
        Long patientId = encounter.getPatient().getId();

        recordView(patientId, loginStaff);

        return SubjectPhi.of(encounter, alertRepository.findActiveByPatientId(patientId));
    }

    private void recordView(Long patientId, LoginStaff loginStaff) {
        HttpServletRequest request =
                RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs
                        ? attrs.getRequest() : null;

        auditRecorder.record(AuditLog.of(
                loginStaff.staffId(), "VIEW", "PATIENT", patientId, patientId,
                request == null ? null : request.getRemoteAddr(),
                request == null ? null : request.getHeader("User-Agent"),
                null));
    }

    /**
     * 목록 한 화면을 채운다.
     *
     * 볼 자격이 없는 가명은 예외를 던지지 않고 빠뜨린다.
     * 큐 한 화면에 섞여 들어온 남의 요청 하나 때문에 화면 전체가 못 뜨면,
     * 간호사는 자기 일까지 못 본다. 대신 그 줄은 이름이 비어 있게 된다.
     */
    public List<SubjectBrief> findBrief(Collection<UUID> subjectRefs, LoginStaff loginStaff) {
        if (subjectRefs == null || subjectRefs.isEmpty()) return List.of();

        List<Encounter> encounters = encounterRepository.findAllBySubjectRefs(subjectRefs).stream()
                .filter(e -> viewable(e, loginStaff))
                .toList();
        if (encounters.isEmpty()) return List.of();

        Map<Long, List<PatientAlert>> alertsByPatient = alertRepository
                .findActiveByPatientIds(encounters.stream()
                        .map(e -> e.getPatient().getId()).distinct().toList())
                .stream()
                .collect(Collectors.groupingBy(a -> a.getPatient().getId()));

        return encounters.stream()
                .map(e -> SubjectBrief.of(e,
                        alertsByPatient.getOrDefault(e.getPatient().getId(), List.of())))
                .toList();
    }

    /**
     * 이 업무 전에 확인할 것.
     *
     * 어떤 항목을 확인해야 하는지는 업무 쪽(업무 항목의 required_alerts)이 알고,
     * 그 사람에게 그 항목이 있는지는 이쪽이 안다. 겹치는 지점이 경고가 된다.
     * 부르는 쪽이 확인 항목을 들고 와야 하는 이유가 그것이다 —
     * 이쪽은 업무 항목이 무엇인지 모르고, 알 이유도 없다.
     */
    public List<ChecklistWarning> checklist(UUID subjectRef, Collection<AlertType> required,
                                            LoginStaff loginStaff) {
        if (required == null || required.isEmpty()) return List.of();

        Encounter encounter = requireViewable(subjectRef, loginStaff);
        return ChecklistWarning.cross(required,
                alertRepository.findActiveByPatientId(encounter.getPatient().getId()));
    }

    /** 이름 일부로 가명을 찾는다. 이름은 밖으로 나가지 않고 가명만 돌려준다. */
    public List<UUID> searchRefs(String namePart, LoginStaff loginStaff) {
        if (namePart == null || namePart.isBlank()) return List.of();

        return encounterRepository.findAdmittedByPatientNameLike(namePart.trim()).stream()
                .filter(e -> viewable(e, loginStaff))
                .map(Encounter::getSubjectRef)
                .toList();
    }

    // ------------------------------------------------------------------

    private Encounter requireViewable(UUID subjectRef, LoginStaff loginStaff) {
        Encounter encounter = encounterRepository.findBySubjectRef(subjectRef)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENCOUNTER_NOT_FOUND));

        if (!viewable(encounter, loginStaff)) {
            throw new BusinessException(ErrorCode.NOT_RELATED_DEPARTMENT);
        }
        return encounter;
    }

    private boolean viewable(Encounter encounter, LoginStaff loginStaff) {
        if (loginStaff.role() == StaffRole.ADMIN) return true;
        if (encounter.getDepartment().getId().equals(loginStaff.departmentId())) return true;

        return workOrderRepository.existsActiveBySubjectAndToDepartment(
                encounter.getSubjectRef(), loginStaff.departmentId(), OrderStatus.terminals());
    }
}
