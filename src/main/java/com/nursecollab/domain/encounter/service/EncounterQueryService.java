package com.nursecollab.domain.encounter.service;

import com.nursecollab.domain.encounter.entity.Encounter;
import com.nursecollab.domain.encounter.repository.EncounterRepository;
import com.nursecollab.domain.phi.service.PhiAccessRecorder;
import com.nursecollab.domain.staff.entity.StaffRole;
import com.nursecollab.domain.phi.port.WorkRelationPort;
import com.nursecollab.global.error.BusinessException;
import com.nursecollab.global.error.ErrorCode;
import com.nursecollab.global.security.LoginStaff;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 재원 건에 대한 접근 판정.
 *
 * 조회 화면은 전부 /phi 로 옮겼다. 예전에는 이 서비스가 /encounters 로
 * 재원 목록과 상세를 내려줬는데, 그 응답에 이름과 진단명이 실려 있었다.
 * 그래서 원내망 밖에서도 병동 보드에만 이름이 그대로 보였다 —
 * 다른 화면은 사라지는데 이 화면만 아니었다.
 *
 * 지금 여기 남은 것은 판정뿐이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EncounterQueryService {

    private final EncounterRepository encounterRepository;
    private final WorkRelationPort workRelation;
    private final PhiAccessRecorder phiAccessRecorder;

    /**
     * 이 환자를 볼 자격이 있는지 판정하고 재원 건을 돌려준다.
     *
     * 주의사항은 재원이 아니라 사람에 붙는다(퇴원한다고 인공관절이 사라지지 않는다).
     * 그런데 접근 판정은 재원 기준이라 여기서 잇는다.
     * 판정을 부르는 쪽마다 다시 구현하면 한쪽만 고쳐지는 날이 오고,
     * 그때 남의 병동 환자에게 주의사항을 붙일 수 있게 된다.
     *
     * @param action 막혔을 때 원내 기록에 무엇을 하려다 막혔는지 남긴다
     */
    public Encounter requireViewableByPatient(Long patientId, LoginStaff loginStaff, String action) {
        Encounter encounter = encounterRepository.findAdmittedByPatientId(patientId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENCOUNTER_NOT_FOUND));

        if (loginStaff.role() == StaffRole.ADMIN
                || encounter.getDepartmentId().equals(loginStaff.departmentId())) {
            return encounter;
        }
        if (!workRelation.hasActiveOrderTo(encounter.getSubjectRef(), loginStaff.departmentId())) {
            phiAccessRecorder.denied(loginStaff, encounter.getSubjectRef(), action, "NOT_RELATED");
            throw new BusinessException(ErrorCode.NOT_RELATED_DEPARTMENT);
        }
        return encounter;
    }
}
