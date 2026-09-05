package com.nursecollab.domain.transfer.entity;

import com.nursecollab.domain.department.entity.Department;
import com.nursecollab.domain.encounter.entity.Encounter;
import com.nursecollab.domain.staff.entity.Staff;
import com.nursecollab.global.common.BaseTimeEntity;
import com.nursecollab.global.error.BusinessException;
import com.nursecollab.global.error.ErrorCode;
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
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "transfer_request")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TransferRequest extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_no", nullable = false, unique = true, length = 30)
    private String requestNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "encounter_id")
    private Encounter encounter;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exam_type_id")
    private ExamType examType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_department_id")
    private Department fromDepartment;   // 요청 파트 (병동)

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_department_id")
    private Department toDepartment;     // 수행 파트 (검사실)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransferStatus status = TransferStatus.REQUESTED;

    @Enumerated(EnumType.STRING)
    @Column(name = "hold_from_status", length = 20)
    private TransferStatus holdFromStatus;  // 보류 직전 상태

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TransferPriority priority = TransferPriority.ROUTINE;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_by")
    private Staff requestedBy;

    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt;

    @Column(name = "desired_at")
    private OffsetDateTime desiredAt;

    @Column(name = "scheduled_at")
    private OffsetDateTime scheduledAt;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(length = 500)
    private String note;

    @Column(name = "hold_reason", length = 500)
    private String holdReason;

    /** 낙관적 락. 두 명이 동시에 처리하는 것을 막는다. */
    @Version
    private Long version;

    // ------------------------------------------------------------------
    // 생성
    // ------------------------------------------------------------------

    /**
     * 이송 요청을 만든다.
     *
     * 수행 파트를 인자로 받지 않는 이유: 검사 종류가 수행 파트를 결정하기 때문이다.
     * 클라이언트가 고를 수 있게 하면 "뇌 MRI 를 CT실로" 같은 조합이 만들어진다.
     */
    public static TransferRequest create(String requestNo,
                                         Encounter encounter,
                                         ExamType examType,
                                         Staff requester,
                                         TransferPriority priority,
                                         OffsetDateTime desiredAt,
                                         String note) {

        if (!encounter.isAdmitted()) {
            throw new BusinessException(ErrorCode.DISCHARGED_ENCOUNTER);
        }
        // 자기 병동에 없는 환자로는 요청을 만들 수 없다.
        // 소속만 보고 판단하지 않고 "이 환자가 우리 병동에 있는가" 라는 관계로 본다.
        if (!encounter.getDepartment().getId().equals(requester.getDepartment().getId())) {
            throw new BusinessException(ErrorCode.NOT_RELATED_DEPARTMENT);
        }

        TransferRequest tr = new TransferRequest();
        tr.requestNo      = requestNo;
        tr.encounter      = encounter;
        tr.examType       = examType;
        tr.fromDepartment = requester.getDepartment();
        tr.toDepartment   = examType.getDepartment();
        tr.requestedBy    = requester;
        tr.priority       = priority;
        tr.desiredAt      = desiredAt;
        tr.note           = note;
        tr.status         = TransferStatus.REQUESTED;
        tr.requestedAt    = OffsetDateTime.now();
        return tr;
    }

    // ------------------------------------------------------------------
    // 행위자 판정
    // ------------------------------------------------------------------

    /**
     * 이 요청에 대해 해당 직원이 어느 쪽인지 판정한다.
     * 어느 쪽도 아니면 접근 권한이 없는 것이다.
     */
    public ActorSide resolveActorSide(Staff staff) {
        Long deptId = staff.getDepartment().getId();
        if (fromDepartment.getId().equals(deptId)) return ActorSide.REQUESTER;
        if (toDepartment.getId().equals(deptId))   return ActorSide.PERFORMER;
        throw new BusinessException(ErrorCode.NOT_RELATED_DEPARTMENT);
    }

    /** 현재 상태에서 이 직원이 누를 수 있는 버튼 목록 */
    public Set<TransferStatus> availableTransitions(Staff staff) {
        // 관계없는 파트면 목록 자체를 볼 수 없다. 상태와 무관하게 먼저 막는다.
        ActorSide side = resolveActorSide(staff);

        if (status.isTerminal()) {
            return Set.of();
        }
        if (status == TransferStatus.ON_HOLD) {
            // 보류 상태에서는 "직전 상태로 복귀" 와 "취소" 만 가능하다
            return new LinkedHashSet<>(Set.of(holdFromStatus, TransferStatus.CANCELLED));
        }
        return TransferStatus.availableFor(status, side);
    }

    // ------------------------------------------------------------------
    // 상태 전이
    // ------------------------------------------------------------------

    /**
     * 상태를 변경한다. 검증에 실패하면 예외를 던지고 상태는 바뀌지 않는다.
     *
     * @param to          목표 상태
     * @param actorSide   행위자 구분
     * @param reason      사유 (보류/취소 시 필수)
     * @param scheduledAt 예정시각 (접수 시 필수)
     */
    public void transitionTo(TransferStatus to,
                             ActorSide actorSide,
                             String reason,
                             OffsetDateTime scheduledAt) {

        if (status.isTerminal()) {
            throw new BusinessException(ErrorCode.ALREADY_FINISHED);
        }

        // 보류 해제는 규칙표가 아니라 저장된 직전 상태로 판단한다
        if (status == TransferStatus.ON_HOLD && to != TransferStatus.CANCELLED) {
            if (to != holdFromStatus) {
                throw new BusinessException(ErrorCode.INVALID_TRANSITION);
            }
            this.status = to;
            this.holdFromStatus = null;
            this.holdReason = null;
            return;
        }

        TransferStatus.Rule rule = TransferStatus.findRule(status, to);
        if (rule == null) {
            throw new BusinessException(ErrorCode.INVALID_TRANSITION);
        }
        if (rule.actorSide() != ActorSide.BOTH && rule.actorSide() != actorSide) {
            throw new BusinessException(ErrorCode.NOT_ALLOWED_ACTOR);
        }
        if (rule.reasonRequired() && (reason == null || reason.isBlank())) {
            throw new BusinessException(ErrorCode.REASON_REQUIRED);
        }
        if (rule.scheduleRequired() && scheduledAt == null) {
            throw new BusinessException(ErrorCode.SCHEDULE_REQUIRED);
        }

        // 보류로 들어갈 때는 돌아올 상태를 기억해 둔다
        if (to == TransferStatus.ON_HOLD) {
            this.holdFromStatus = this.status;
            this.holdReason = reason;
        }

        // 상태별 시각 기록
        switch (to) {
            case ACCEPTED    -> this.scheduledAt = scheduledAt;
            case IN_PROGRESS -> this.startedAt   = OffsetDateTime.now();
            case COMPLETED   -> this.completedAt = OffsetDateTime.now();
            case CANCELLED   -> this.holdReason  = reason;
            default          -> { /* 별도 기록 없음 */ }
        }

        this.status = to;
    }
}
