package com.nursecollab.domain.phi.dto;

import com.nursecollab.domain.patient.entity.Patient;
import com.nursecollab.domain.phi.entity.PhiAccessLog;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 원내 열람 기록 한 줄 (A-05).
 *
 * 누가 열었는지는 아이디와 소속 id 까지만 담는다. 직원 이름과 부서 이름은
 * 업무 쪽에 있다 — 기록이 쌓일 때 아이디를 굳혀 둔 이유가 그것이다.
 *
 * @param detail 간호기록 수정처럼 전후 내용이 있는 경우에만 채워진다.
 *               수정 전 내용은 그 자체가 진료정보라 원내 밖으로 나가지 않는다.
 */
public record PhiAccessLogResponse(
        Long id,
        OffsetDateTime occurredAt,
        String action,
        boolean granted,
        String deniedReason,
        ActorInfo actor,
        PatientInfo patient,
        String ipAddress,
        Map<String, Object> detail
) {
    public record ActorInfo(Long id, String loginId, Long departmentId) {}

    public record PatientInfo(String patientNo, String name) {}

    public static PhiAccessLogResponse of(PhiAccessLog log, Patient patient) {
        return new PhiAccessLogResponse(
                log.getId(),
                log.getOccurredAt(),
                log.getAction(),
                log.isGranted(),
                log.getDeniedReason(),
                new ActorInfo(log.getActorId(), log.getActorLoginId(), log.getActorDeptId()),
                patient == null ? null : new PatientInfo(patient.getPatientNo(), patient.getName()),
                log.getIpAddress() == null ? null : log.getIpAddress().getHostAddress(),
                log.getDetail());
    }
}
