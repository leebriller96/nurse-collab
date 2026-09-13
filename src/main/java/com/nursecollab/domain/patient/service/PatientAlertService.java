package com.nursecollab.domain.patient.service;

import com.nursecollab.domain.encounter.entity.Encounter;
import com.nursecollab.domain.patient.dto.AlertResponse;
import com.nursecollab.domain.encounter.service.EncounterQueryService;
import com.nursecollab.domain.patient.dto.AlertCreateRequest;
import com.nursecollab.domain.patient.entity.Patient;
import com.nursecollab.domain.patient.entity.PatientAlert;
import com.nursecollab.domain.patient.repository.PatientAlertRepository;
import com.nursecollab.domain.patient.repository.PatientRepository;
import com.nursecollab.domain.phi.service.PhiAccessRecorder;
import com.nursecollab.global.error.BusinessException;
import com.nursecollab.global.error.ErrorCode;
import com.nursecollab.global.security.LoginStaff;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 환자 주의사항.
 *
 * 낙상 위험, 폐소공포 이력, 격리처럼 **곁에서 본 것**을 간호사가 남긴다.
 * EMR 의 진단명이 아니라 간호 관찰이라 이 시스템이 갖는다.
 *
 * 이것이 업무 항목의 확인 항목과 만나 "이 검사 전에 확인이 필요합니다" 가 된다.
 * 이 프로젝트에서 가장 특징적인 화면이 여기에 기대고 있다.
 *
 * <p>누가 남기고 내렸는지는 원내 접근 기록에 남는다. 저장을 먼저 확정하고 남긴다 —
 * 기록은 따로 커밋되므로, 순서가 바뀌면 실패한 저장이 "남겼다" 로 기록된다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PatientAlertService {

    private final PatientAlertRepository alertRepository;
    private final PatientRepository patientRepository;
    private final EncounterQueryService encounterQueryService;
    private final PhiAccessRecorder phiAccessRecorder;

    @Transactional
    public AlertResponse add(Long patientId, AlertCreateRequest request, LoginStaff loginStaff) {
        // 환자 조회와 같은 판정을 거친다. 남의 병동 환자에게 붙일 수 있으면 안 된다.
        Encounter encounter =
                encounterQueryService.requireViewableByPatient(patientId, loginStaff, "ALERT_CREATE");

        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENCOUNTER_NOT_FOUND));

        PatientAlert alert = alertRepository.saveAndFlush(
                PatientAlert.create(patient, request.alertType(), request.severity(),
                        request.content(), loginStaff.staffId()));
        phiAccessRecorder.granted(loginStaff, encounter.getSubjectRef(), patientId, "ALERT_CREATE");

        return AlertResponse.from(alert);
    }

    /**
     * 지우지 않고 내린다.
     * 이 주의사항을 보고 판단한 지난 요청이 있다. 지우면 그 판단의 근거가 사라진다.
     */
    @Transactional
    public void deactivate(Long alertId, LoginStaff loginStaff) {
        PatientAlert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ALERT_NOT_FOUND));

        Long patientId = alert.getPatient().getId();
        Encounter encounter =
                encounterQueryService.requireViewableByPatient(patientId, loginStaff, "ALERT_DEACTIVATE");
        alert.deactivate();
        alertRepository.flush();
        phiAccessRecorder.granted(loginStaff, encounter.getSubjectRef(), patientId, "ALERT_DEACTIVATE");
    }
}
