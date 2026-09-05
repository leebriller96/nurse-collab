package com.nursecollab.global.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 접근·변경 기록.
 * 월 단위로 파티션된 테이블이라 occurred_at 이 저장 위치를 결정한다.
 */
@Entity
@Table(name = "audit_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(nullable = false, length = 30)
    private String action;

    @Column(name = "target_type", nullable = false, length = 50)
    private String targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "patient_id")
    private Long patientId;

    // 컬럼이 inet 이라 문자열로 두면 바인딩이 안 된다. Hibernate 가 아는 타입으로 받는다.
    @JdbcTypeCode(SqlTypes.INET)
    @Column(name = "ip_address")
    private InetAddress ipAddress;

    @Column(name = "user_agent", length = 300)
    private String userAgent;

    // String 으로 두면 Hibernate 가 JSONB 로 바꾸지 못한다. 객체로 두고 직렬화를 맡긴다.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> detail;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private OffsetDateTime occurredAt;

    public static AuditLog of(Long actorId, String action, String targetType, Long targetId,
                              Long patientId, String ipAddress, String userAgent,
                              Map<String, Object> detail) {
        AuditLog log = new AuditLog();
        log.actorId = actorId;
        log.action = action;
        log.targetType = targetType;
        log.targetId = targetId;
        log.patientId = patientId;
        log.ipAddress = parseIp(ipAddress);
        log.userAgent = userAgent;
        log.detail = detail;
        log.occurredAt = OffsetDateTime.now();
        return log;
    }

    /** 숫자 형태의 주소만 들어오므로 이름 조회가 발생하지 않는다. */
    private static InetAddress parseIp(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return InetAddress.getByName(raw);
        } catch (UnknownHostException e) {
            return null;
        }
    }
}
