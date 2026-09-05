package com.nursecollab.domain.encounter.service;

import com.nursecollab.domain.encounter.repository.EncounterRepository;
import com.nursecollab.global.audit.AuditTargetResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 재원 식별자로 어느 환자인지 알아낸다. 감사 로그에 환자를 함께 남기기 위한 것이다. */
@Component
@RequiredArgsConstructor
public class EncounterAuditTargetResolver implements AuditTargetResolver {

    private final EncounterRepository encounterRepository;

    @Override
    public String targetType() {
        return "ENCOUNTER";
    }

    @Override
    @Transactional(readOnly = true)
    public Long resolvePatientId(Long encounterId) {
        return encounterRepository.findByIdWithPatientAndDepartment(encounterId)
                .map(e -> e.getPatient().getId())
                .orElse(null);
    }
}
