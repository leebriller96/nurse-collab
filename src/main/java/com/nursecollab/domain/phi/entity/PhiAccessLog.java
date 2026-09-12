package com.nursecollab.domain.phi.entity;

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
import java.util.UUID;

/**
 * 원내가 스스로 남기는 접근 기록.
 *
 * 클라우드의 {@code audit_log} 와 별개다. 거기 있는 기록은 클라우드가 뚫리면
 * 같이 지워지거나 고쳐질 수 있다. 진료정보가 실제로 나간 곳은 원내이므로
 * 그 기록도 원내에 있어야 한다.
 *
 * <p><b>거절된 시도도 남긴다.</b> 업무 쪽 감사 AOP 는 성공한 요청만 적는데,
 * 조사할 때 제일 보고 싶은 것은 "누가 볼 수 없는 것을 열려고 했는가" 다.
 *
 * <p>직원 정보에 외래키를 걸지 않고 아이디를 문자열로 함께 적는다.
 * 이 로그는 업무 쪽 DB 가 없어도 그것만으로 읽을 수 있어야 한다.
 */
@Entity
@Table(name = "phi_access_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PhiAccessLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_id", nullable = false)
    private Long actorId;

    @Column(name = "actor_login_id", nullable = false, length = 50)
    private String actorLoginId;

    @Column(name = "actor_dept_id")
    private Long actorDeptId;

    @Column(name = "subject_ref", nullable = false)
    private UUID subjectRef;

    /** 거절된 시도에서는 누구인지 확인하기 전에 막히므로 비어 있을 수 있다. */
    @Column(name = "patient_id")
    private Long patientId;

    @Column(nullable = false, length = 20)
    private String action;

    @Column(nullable = false)
    private boolean granted;

    @Column(name = "denied_reason", length = 100)
    private String deniedReason;

    // 컬럼이 inet 이라 문자열로 두면 바인딩이 안 된다. Hibernate 가 아는 타입으로 받는다.
    @JdbcTypeCode(SqlTypes.INET)
    @Column(name = "ip_address")
    private InetAddress ipAddress;

    @Column(name = "user_agent", length = 300)
    private String userAgent;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    public static PhiAccessLog granted(Long actorId, String loginId, Long deptId,
                                       UUID subjectRef, Long patientId, String action,
                                       String ip, String userAgent) {
        return log(actorId, loginId, deptId, subjectRef, patientId, action,
                true, null, ip, userAgent);
    }

    public static PhiAccessLog denied(Long actorId, String loginId, Long deptId,
                                      UUID subjectRef, String action, String reason,
                                      String ip, String userAgent) {
        return log(actorId, loginId, deptId, subjectRef, null, action,
                false, reason, ip, userAgent);
    }

    private static PhiAccessLog log(Long actorId, String loginId, Long deptId,
                                    UUID subjectRef, Long patientId, String action,
                                    boolean granted, String deniedReason,
                                    String ip, String userAgent) {
        PhiAccessLog entry = new PhiAccessLog();
        entry.actorId = actorId;
        entry.actorLoginId = loginId;
        entry.actorDeptId = deptId;
        entry.subjectRef = subjectRef;
        entry.patientId = patientId;
        entry.action = action;
        entry.granted = granted;
        entry.deniedReason = deniedReason;
        entry.ipAddress = parseIp(ip);
        // 길이를 넘기면 INSERT 가 통째로 실패한다. 기록을 못 남기느니 잘라서 남긴다.
        entry.userAgent = userAgent == null || userAgent.length() <= 300
                ? userAgent : userAgent.substring(0, 300);
        entry.occurredAt = OffsetDateTime.now();
        return entry;
    }

    /** 주소를 못 알아보면 비워 둔다. 주소 하나 때문에 기록 자체를 잃지 않는다. */
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
