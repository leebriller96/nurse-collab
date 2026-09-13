package com.nursecollab.domain.audit.dto;

import com.nursecollab.global.audit.AuditLog;

import java.time.OffsetDateTime;

/**
 * 업무 쪽 감사 기록 한 줄.
 *
 * 환자 칸이 없다. 환자 정보를 열어본 기록은 원내(phi_access_log)에 남고
 * A-05 화면도 그쪽을 읽는다. 여기서 환자를 붙이려면 업무 쪽이 환자 테이블을 읽어야 하는데,
 * 그 순간 나눈 의미가 사라진다.
 */
public record AuditLogResponse(
        Long id,
        ActorInfo actor,
        String action,
        String targetType,
        Long targetId,
        String ipAddress,
        OffsetDateTime occurredAt
) {
    public record ActorInfo(Long id, String name, String departmentName) {}

    public static AuditLogResponse of(AuditLog log, ActorInfo actor) {
        return new AuditLogResponse(
                log.getId(), actor, log.getAction(), log.getTargetType(),
                log.getTargetId(),
                log.getIpAddress() == null ? null : log.getIpAddress().getHostAddress(),
                log.getOccurredAt());
    }
}
