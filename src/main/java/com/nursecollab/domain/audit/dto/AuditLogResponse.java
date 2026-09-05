package com.nursecollab.domain.audit.dto;

import com.nursecollab.global.audit.AuditLog;

import java.time.OffsetDateTime;

public record AuditLogResponse(
        Long id,
        ActorInfo actor,
        String action,
        String targetType,
        Long targetId,
        PatientInfo patient,
        String ipAddress,
        OffsetDateTime occurredAt
) {
    public record ActorInfo(Long id, String name, String departmentName) {}

    public record PatientInfo(Long id, String patientNo, String name) {}

    public static AuditLogResponse of(AuditLog log, ActorInfo actor, PatientInfo patient) {
        return new AuditLogResponse(
                log.getId(), actor, log.getAction(), log.getTargetType(),
                log.getTargetId(), patient,
                log.getIpAddress() == null ? null : log.getIpAddress().getHostAddress(),
                log.getOccurredAt());
    }
}
