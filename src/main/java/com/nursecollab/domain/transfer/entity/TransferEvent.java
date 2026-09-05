package com.nursecollab.domain.transfer.entity;

import com.nursecollab.domain.department.entity.Department;
import com.nursecollab.domain.staff.entity.Staff;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 상태 전이 이력. 한 번 쌓이면 절대 수정하지 않는다(append only).
 * 대기시간 통계는 전부 이 테이블에서 계산된다.
 */
@Entity
@Table(name = "transfer_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TransferEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id")
    private TransferRequest request;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20)
    private TransferStatus fromStatus;   // 최초 생성 시 null

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20)
    private TransferStatus toStatus;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id")
    private Staff actor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_dept_id")
    private Department actorDept;

    @Column(length = 500)
    private String reason;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    public static TransferEvent of(TransferRequest request,
                                   TransferStatus from,
                                   TransferStatus to,
                                   Staff actor,
                                   String reason) {
        TransferEvent e = new TransferEvent();
        e.request    = request;
        e.fromStatus = from;
        e.toStatus   = to;
        e.actor      = actor;
        e.actorDept  = actor.getDepartment();
        e.reason     = reason;
        e.occurredAt = OffsetDateTime.now();
        return e;
    }
}
