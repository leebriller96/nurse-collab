package com.nursecollab.domain.phi.service;

import com.nursecollab.domain.encounter.entity.Encounter;
import com.nursecollab.domain.encounter.repository.EncounterRepository;
import com.nursecollab.domain.patient.dto.AlertCreateRequest;
import com.nursecollab.domain.patient.dto.AlertResponse;
import com.nursecollab.domain.patient.dto.ChecklistWarning;
import com.nursecollab.domain.patient.entity.AlertType;
import com.nursecollab.domain.patient.entity.PatientAlert;
import com.nursecollab.domain.patient.repository.PatientAlertRepository;
import com.nursecollab.domain.patient.service.PatientAlertService;
import com.nursecollab.domain.phi.dto.SubjectBrief;
import com.nursecollab.domain.phi.repository.PhiAccessLogRepository;
import com.nursecollab.domain.phi.dto.SubjectPhi;
import com.nursecollab.domain.staff.entity.StaffRole;
import com.nursecollab.domain.phi.port.WorkRelationPort;
import com.nursecollab.global.error.BusinessException;
import com.nursecollab.global.error.ErrorCode;
import com.nursecollab.global.security.LoginStaff;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
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
    private final WorkRelationPort workRelation;
    private final PhiAccessRecorder phiAccessRecorder;
    private final PhiAccessLogRepository phiAccessLogRepository;
    private final PatientAlertService patientAlertService;

    /**
     * 조회량 제한.
     *
     * 클라우드가 뚫리면 간호사 토큰을 새로 발급해 원내에 물어볼 수 있다.
     * 인증 서버가 클라우드인 이상 이 구멍은 구조적으로 남는다. 막지는 못한다.
     * 대신 <b>한 번에 많이 긁어가는 것</b>을 막고 흔적을 남긴다.
     *
     * 요청 수가 아니라 서로 다른 사람 수를 센다. 같은 환자를 다섯 번 여는 것은
     * 정상 근무이고, 스무 명을 한 번씩 여는 것이 이상한 일이다.
     * 간호사 한 명이 한 근무에 맡는 환자가 10~15명이라 30명이면 넉넉하다.
     *
     * 관리자도 예외로 두지 않는다. 관리자 계정이야말로 노리는 쪽이 제일 갖고 싶어 하는 것이다.
     */
    private static final Duration RATE_WINDOW = Duration.ofMinutes(10);
    private static final long RATE_LIMIT_PATIENTS = 30;

    /**
     * 한 사람을 연다. 누가 언제 열었는지 원내 접근 기록에 남는다.
     *
     * 전에는 업무 쪽 감사 로그에도 한 줄씩 겹쳐 적었다. 거기엔 환자 id 가 실리므로
     * 업무 쪽이 "누가 어느 환자를 열었나" 를 쥐고 있게 된다. 원내에만 남긴다.
     */
    public SubjectPhi findOne(UUID subjectRef, LoginStaff loginStaff) {
        Encounter encounter = requireViewable(subjectRef, loginStaff, "VIEW");
        Long patientId = encounter.getPatient().getId();

        if (rateLimited(loginStaff)) {
            phiAccessRecorder.denied(loginStaff, subjectRef, "VIEW", "RATE_LIMITED");
            throw new BusinessException(ErrorCode.PHI_RATE_LIMITED);
        }

        // 원내 기록이 먼저다. 이것이 남지 않으면 무엇이 나갔는지 알 수 없다.
        phiAccessRecorder.granted(loginStaff, subjectRef, patientId, "VIEW");

        return SubjectPhi.of(encounter, alertRepository.findActiveByPatientId(patientId));
    }

    private boolean rateLimited(LoginStaff loginStaff) {
        return phiAccessLogRepository.countDistinctPatientsSince(
                loginStaff.staffId(), OffsetDateTime.now().minus(RATE_WINDOW))
                >= RATE_LIMIT_PATIENTS;
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

        List<Encounter> encounters = viewableOnly(
                encounterRepository.findAllBySubjectRefs(subjectRefs), loginStaff);
        if (encounters.isEmpty()) return List.of();

        Map<Long, List<PatientAlert>> alertsByPatient = alertRepository
                .findActiveByPatientIds(encounters.stream()
                        .map(e -> e.getPatient().getId()).distinct().toList())
                .stream()
                .collect(Collectors.groupingBy(a -> a.getPatient().getId()));

        return encounters.stream()
                .map(e -> SubjectBrief.of(e,
                        alertsByPatient.getOrDefault(e.getPatient().getId(), List.of()),
                        // 담당 병동이면 진단명과 주의사항 뱃지까지 받는다.
                        // 검사실은 이름과 "주의할 것이 몇 건" 까지만 본다.
                        ownWard(e, loginStaff)))
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

        Encounter encounter = requireViewable(subjectRef, loginStaff, "CHECKLIST");
        return ChecklistWarning.cross(required,
                alertRepository.findActiveByPatientId(encounter.getPatient().getId()));
    }

    /**
     * 주의사항을 남긴다. 붙는 곳은 재원이 아니라 사람이다.
     * 판정은 조회와 같은 것을 쓴다 — 남의 병동 환자에게 붙일 수 있으면 안 된다.
     */
    @Transactional
    public AlertResponse addAlert(UUID subjectRef, AlertCreateRequest request,
                                  LoginStaff loginStaff) {
        Encounter encounter = requireViewable(subjectRef, loginStaff, "ALERT_CREATE");
        return patientAlertService.add(encounter.getPatient().getId(), request, loginStaff);
    }

    @Transactional
    public void deactivateAlert(Long alertId, LoginStaff loginStaff) {
        patientAlertService.deactivate(alertId, loginStaff);
    }

    /** 이 사람에게 지금 붙어 있는 주의사항. 상세 화면이 목록으로 보여준다. */
    public List<AlertResponse> findAlerts(UUID subjectRef, LoginStaff loginStaff) {
        Encounter encounter = requireViewable(subjectRef, loginStaff, "VIEW");
        return alertRepository.findActiveByPatientId(encounter.getPatient().getId()).stream()
                .map(AlertResponse::from)
                .toList();
    }

    /** 이름 일부로 가명을 찾는다. 이름은 밖으로 나가지 않고 가명만 돌려준다. */
    public List<UUID> searchRefs(String namePart, LoginStaff loginStaff) {
        if (namePart == null || namePart.isBlank()) return List.of();

        return viewableOnly(encounterRepository.findAdmittedByPatientNameLike(namePart.trim()),
                loginStaff).stream()
                .map(Encounter::getSubjectRef)
                .toList();
    }

    // ------------------------------------------------------------------

    private Encounter requireViewable(UUID subjectRef, LoginStaff loginStaff, String action) {
        Encounter encounter = encounterRepository.findBySubjectRef(subjectRef)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENCOUNTER_NOT_FOUND));

        if (!viewable(encounter, loginStaff)) {
            // 막힌 시도야말로 조사할 때 제일 보고 싶은 것이다.
            // 업무 쪽 감사 로그는 성공한 요청만 적으므로 여기서 남겨야 한다.
            phiAccessRecorder.denied(loginStaff, subjectRef, action, "NOT_RELATED");
            throw new BusinessException(ErrorCode.NOT_RELATED_DEPARTMENT);
        }
        return encounter;
    }

    /** 담당 병동이거나 관리자인가. 검사실은 여기에 해당하지 않는다. */
    private boolean ownWard(Encounter encounter, LoginStaff loginStaff) {
        return loginStaff.role() == StaffRole.ADMIN
                || encounter.getDepartmentId().equals(loginStaff.departmentId());
    }

    private boolean viewable(Encounter encounter, LoginStaff loginStaff) {
        if (ownWard(encounter, loginStaff)) return true;

        return workRelation.hasActiveOrderTo(encounter.getSubjectRef(), loginStaff.departmentId());
    }

    /**
     * 목록에서 볼 자격이 있는 것만 남긴다.
     * 한 명씩 물으면 서버가 갈라진 뒤 요청이 사람 수만큼 나간다. 한 번에 묻는다.
     */
    private List<Encounter> viewableOnly(List<Encounter> encounters, LoginStaff loginStaff) {
        List<Encounter> others = encounters.stream().filter(e -> !ownWard(e, loginStaff)).toList();
        java.util.Set<UUID> related = others.isEmpty() ? java.util.Set.of()
                : workRelation.subjectsWithActiveOrdersTo(
                        others.stream().map(Encounter::getSubjectRef).toList(),
                        loginStaff.departmentId());

        return encounters.stream()
                .filter(e -> ownWard(e, loginStaff) || related.contains(e.getSubjectRef()))
                .toList();
    }
}
