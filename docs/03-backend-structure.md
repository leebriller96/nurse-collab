# 병원 파트 간 간호 협업 시스템 - 백엔드 구조 및 핵심 코드 v1

작성: 2026-09-04 (KST)
Java 21 / Spring Boot 3.3 / PostgreSQL 16 / Redis

---

## 0. 스키마 수정사항 (v1 DDL 보완)

보류(ON_HOLD) 후 **직전 상태로 복귀**하려면 직전 상태를 알아야 한다.
이력 테이블을 역추적할 수도 있지만 매번 조회하는 건 낭비다. 컬럼 하나를 추가한다.

```sql
-- V2__add_hold_from_status.sql
ALTER TABLE transfer_request
    ADD COLUMN hold_from_status VARCHAR(20);

COMMENT ON COLUMN transfer_request.hold_from_status IS
'ON_HOLD 진입 직전 상태. 보류 해제 시 이 상태로 복귀한다';
```

---

## 1. 패키지 구조

기술별(controller/service/repository)이 아니라 **도메인별**로 나눈다.
기능 하나를 고칠 때 폴더 하나만 열면 되게 만드는 것이 목적이다.

```
com.nursecollab
├── NurseCollabApplication.java
│
├── global/                          # 도메인과 무관한 공통 기반
│   ├── config/
│   │   ├── SecurityConfig.java
│   │   ├── WebSocketConfig.java
│   │   ├── JpaConfig.java           # Auditing 활성화
│   │   └── RedisConfig.java
│   ├── security/
│   │   ├── JwtTokenProvider.java
│   │   ├── JwtAuthenticationFilter.java
│   │   ├── JwtProperties.java           # 시크릿 / 만료시간 설정
│   │   ├── LoginStaff.java              # @AuthenticationPrincipal 로 받는 인증 주체
│   │   ├── RefreshTokenStore.java       # 갱신 토큰 Redis 보관 (로그아웃 / 회전)
│   │   └── SecurityErrorResponder.java  # 필터 단계 401 / 403 응답
│   ├── error/
│   │   ├── ErrorCode.java           # 에러코드 + 메시지 + HTTP 상태 일괄 관리
│   │   ├── BusinessException.java
│   │   ├── ErrorResponse.java
│   │   └── GlobalExceptionHandler.java
│   ├── audit/
│   │   ├── Audited.java             # @Audited 어노테이션
│   │   ├── AuditAspect.java         # AOP 로 audit_log 자동 적재
│   │   └── AuditLog.java
│   └── common/
│       ├── BaseTimeEntity.java      # createdAt / updatedAt 공통
│       └── PageResponse.java
│
├── domain/
│   ├── department/                  # 파트 마스터
│   ├── staff/                       # 계정 + 인증
│   ├── patient/                     # 환자, 주의사항(alert)
│   ├── encounter/                   # 재원 + 파트별 뷰 조립
│   ├── transfer/                    # ★ 이송 요청 (이 프로젝트의 심장)
│   │   ├── entity/
│   │   │   ├── TransferRequest.java
│   │   │   ├── TransferEvent.java
│   │   │   ├── TransferStatus.java      # 상태 + 전이 규칙
│   │   │   ├── TransferPriority.java
│   │   │   └── ActorSide.java
│   │   ├── repository/
│   │   ├── service/
│   │   │   ├── TransferRequestService.java
│   │   │   └── TransferQueryService.java
│   │   ├── controller/
│   │   ├── dto/
│   │   └── event/                       # 도메인 이벤트 (알림 발송 트리거)
│   ├── nursing/                     # 활력징후, 간호기록
│   ├── notification/                # 알림함
│   └── stats/                       # 대기시간 통계 (집계는 SQL 로)
│
└── infra/
    ├── realtime/
    │   └── RealtimeNotifier.java    # STOMP 발송 담당
    └── storage/
```

**핵심 원칙**
- `domain` 은 `global` 을 참조해도 되지만, `global` 이 `domain` 을 참조하면 안 된다
- 도메인 간 참조는 서비스 레이어를 통해서만 한다 (엔티티 직접 참조는 최소화)

**인증 주체를 컨트롤러로 넘기는 방법**

`LoginStaff` 를 Authentication 의 principal 로 넣기 때문에
`@AuthenticationPrincipal` 이 그대로 동작한다. 별도의 ArgumentResolver 는 두지 않는다.

**토큰에 담는 것과 담지 않는 것**

접근 토큰에는 소속 파트와 역할을 실어서 요청마다 `staff` 를 다시 조회하지 않게 한다.
갱신 토큰에는 식별자만 담는다. 탈취되더라도 그 자체로는 아무 정보가 되지 않게 하기 위해서다.
소속이 바뀌면 접근 토큰이 만료될 때까지 옛 소속이 남는다는 점은 감수한다.

---

## 2. 상태 전이 코드 (프로젝트의 핵심)

### 2-1. ActorSide - 행위자가 어느 쪽인가

```java
package com.nursecollab.domain.transfer.entity;

/**
 * 상태를 변경하려는 사람이 요청 파트(병동)인지 수행 파트(검사실)인지를 구분한다.
 * 같은 상태라도 누가 누르느냐에 따라 허용 여부가 달라지기 때문에 필요하다.
 */
public enum ActorSide {
    REQUESTER,  // 요청 파트 (병동)
    PERFORMER,  // 수행 파트 (검사실)
    BOTH        // 양쪽 모두 가능
}
```

### 2-2. TransferStatus - 상태와 전이 규칙을 한 곳에

```java
package com.nursecollab.domain.transfer.entity;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 이송 요청의 상태.
 *
 * 전이 규칙을 이 enum 안에 모아두는 이유:
 * 규칙이 서비스 코드 여기저기에 if 문으로 흩어지면
 * 상태를 하나 추가할 때 어디를 고쳐야 하는지 아무도 모르게 된다.
 */
public enum TransferStatus {

    REQUESTED("요청됨"),
    ACCEPTED("접수됨"),
    READY("준비완료"),
    IN_TRANSIT("이송중"),
    IN_PROGRESS("검사중"),
    RETURNED("복귀중"),
    COMPLETED("완료"),
    ON_HOLD("보류"),
    CANCELLED("취소");

    private final String label;

    TransferStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** 더 이상 상태가 바뀌지 않는 종료 상태인가 */
    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED;
    }

    /**
     * 하나의 전이 규칙.
     *
     * @param from            시작 상태
     * @param to              도착 상태
     * @param actorSide       이 전이를 누를 수 있는 쪽
     * @param reasonRequired  사유 입력이 필수인가
     * @param scheduleRequired 예정시각 입력이 필수인가
     */
    public record Rule(
            TransferStatus from,
            TransferStatus to,
            ActorSide actorSide,
            boolean reasonRequired,
            boolean scheduleRequired
    ) {}

    /**
     * 전체 전이 규칙표.
     * ON_HOLD 에서 원래 상태로 복귀하는 것은 상태값이 동적이라 여기 넣지 않고
     * TransferRequest.transitionTo() 가 저장된 직전 상태를 보고 따로 처리한다.
     */
    private static final List<Rule> RULES = List.of(
            // 요청됨 → 접수 / 보류 / 취소
            new Rule(REQUESTED,   ACCEPTED,    ActorSide.PERFORMER, false, true),
            new Rule(REQUESTED,   ON_HOLD,     ActorSide.PERFORMER, true,  false),
            new Rule(REQUESTED,   CANCELLED,   ActorSide.BOTH,      true,  false),

            // 접수됨 → 준비완료 / 보류 / 취소
            new Rule(ACCEPTED,    READY,       ActorSide.PERFORMER, false, false),
            new Rule(ACCEPTED,    ON_HOLD,     ActorSide.PERFORMER, true,  false),
            new Rule(ACCEPTED,    CANCELLED,   ActorSide.BOTH,      true,  false),

            // 준비완료 → 이송중 / 보류 / 취소
            new Rule(READY,       IN_TRANSIT,  ActorSide.REQUESTER, false, false),
            new Rule(READY,       ON_HOLD,     ActorSide.BOTH,      true,  false),
            new Rule(READY,       CANCELLED,   ActorSide.BOTH,      true,  false),

            // 이송중 → 검사중 / 보류
            new Rule(IN_TRANSIT,  IN_PROGRESS, ActorSide.PERFORMER, false, false),
            new Rule(IN_TRANSIT,  ON_HOLD,     ActorSide.BOTH,      true,  false),

            // 검사중 → 복귀중
            new Rule(IN_PROGRESS, RETURNED,    ActorSide.PERFORMER, false, false),

            // 복귀중 → 완료
            new Rule(RETURNED,    COMPLETED,   ActorSide.REQUESTER, false, false),

            // 보류 → 취소 (복귀는 직전 상태로 돌아가므로 규칙표에 없다)
            new Rule(ON_HOLD,     CANCELLED,   ActorSide.BOTH,      true,  false)
    );

    /** from → to 전이 규칙을 찾는다. 없으면 허용되지 않는 전이다. */
    public static Rule findRule(TransferStatus from, TransferStatus to) {
        return RULES.stream()
                .filter(r -> r.from() == from && r.to() == to)
                .findFirst()
                .orElse(null);
    }

    /**
     * 특정 상태에서 특정 행위자가 누를 수 있는 상태 목록.
     * API 응답의 availableTransitions 가 이 메서드 결과다.
     */
    public static Set<TransferStatus> availableFor(TransferStatus current, ActorSide side) {
        return RULES.stream()
                .filter(r -> r.from() == current)
                .filter(r -> r.actorSide() == ActorSide.BOTH || r.actorSide() == side)
                .map(Rule::to)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    public static TransferStatus from(String value) {
        return Arrays.stream(values())
                .filter(s -> s.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("알 수 없는 상태: " + value));
    }
}
```

### 2-3. TransferRequest 엔티티

```java
package com.nursecollab.domain.transfer.entity;

import com.nursecollab.domain.department.entity.Department;
import com.nursecollab.domain.encounter.entity.Encounter;
import com.nursecollab.domain.staff.entity.Staff;
import com.nursecollab.global.common.BaseTimeEntity;
import com.nursecollab.global.error.BusinessException;
import com.nursecollab.global.error.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Set;

@Entity
@Table(name = "transfer_request")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TransferRequest extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String requestNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Encounter encounter;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
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
    @Column(length = 20)
    private TransferStatus holdFromStatus;  // 보류 직전 상태

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TransferPriority priority = TransferPriority.ROUTINE;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Staff requestedBy;

    @Column(nullable = false)
    private OffsetDateTime requestedAt;

    private OffsetDateTime desiredAt;
    private OffsetDateTime scheduledAt;
    private OffsetDateTime startedAt;
    private OffsetDateTime completedAt;

    @Column(length = 500)
    private String note;

    @Column(length = 500)
    private String holdReason;

    /** 낙관적 락. 두 명이 동시에 처리하는 것을 막는다. */
    @Version
    private Long version;

    // ------------------------------------------------------------------
    // 생성
    // ------------------------------------------------------------------
    public static TransferRequest create(String requestNo,
                                         Encounter encounter,
                                         ExamType examType,
                                         Staff requester,
                                         TransferPriority priority,
                                         OffsetDateTime desiredAt,
                                         String note) {
        TransferRequest tr = new TransferRequest();
        tr.requestNo      = requestNo;
        tr.encounter      = encounter;
        tr.examType       = examType;
        tr.fromDepartment = requester.getDepartment();
        tr.toDepartment   = examType.getDepartment();   // 검사 종류가 수행 파트를 결정
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

        if (status.isTerminal()) return Set.of();
        if (status == TransferStatus.ON_HOLD) {
            // 보류 상태에서는 "복귀" 와 "취소" 만 가능
            return Set.of(holdFromStatus, TransferStatus.CANCELLED);
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
```

### 2-4. TransferEvent 엔티티

```java
package com.nursecollab.domain.transfer.entity;

import com.nursecollab.domain.department.entity.Department;
import com.nursecollab.domain.staff.entity.Staff;
import jakarta.persistence.*;
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
    @Column(length = 20)
    private TransferStatus fromStatus;   // 최초 생성 시 null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransferStatus toStatus;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Staff actor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_dept_id")
    private Department actorDept;

    @Column(length = 500)
    private String reason;

    @Column(nullable = false)
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
```

### 2-5. 서비스

```java
package com.nursecollab.domain.transfer.service;

import com.nursecollab.domain.staff.entity.Staff;
import com.nursecollab.domain.staff.repository.StaffRepository;
import com.nursecollab.domain.transfer.dto.*;
import com.nursecollab.domain.transfer.entity.*;
import com.nursecollab.domain.transfer.event.TransferStatusChangedEvent;
import com.nursecollab.domain.transfer.repository.*;
import com.nursecollab.global.error.BusinessException;
import com.nursecollab.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TransferRequestService {

    private final TransferRequestRepository requestRepository;
    private final TransferEventRepository   eventRepository;
    private final StaffRepository           staffRepository;
    private final RequestNoGenerator        requestNoGenerator;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 상태 전이 처리.
     *
     * 흐름: 조회 → 버전확인 → 행위자판정 → 상태변경(엔티티가 검증) → 이력적재 → 이벤트발행
     * 알림 발송은 여기서 직접 하지 않고 도메인 이벤트로 넘긴다.
     * 트랜잭션이 롤백됐는데 알림만 나가는 사고를 막기 위해서다.
     */
    @Transactional
    public TransitionResponse transition(Long requestId,
                                             TransitionRequest req,
                                             Long staffId) {

        TransferRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REQUEST_NOT_FOUND));

        Staff actor = staffRepository.findById(staffId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STAFF_NOT_FOUND));

        // 1. 클라이언트가 보고 있던 버전과 현재 버전이 같은지 먼저 확인한다.
        //    (JPA @Version 도 flush 시점에 검사하지만, 미리 막아야 사용자에게 친절한 메시지를 줄 수 있다)
        if (!request.getVersion().equals(req.version())) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }

        ActorSide side = request.resolveActorSide(actor);
        TransferStatus fromStatus = request.getStatus();
        TransferStatus toStatus   = TransferStatus.from(req.toStatus());

        // 2. 검증과 상태 변경은 엔티티가 스스로 한다 (서비스에 if 문을 쌓지 않는다)
        request.transitionTo(toStatus, side, req.reason(), req.scheduledAt());

        // 3. 이력 적재
        eventRepository.save(
                TransferEvent.of(request, fromStatus, toStatus, actor, req.reason()));

        // 4. 알림은 커밋 이후에 나가도록 이벤트만 발행
        eventPublisher.publishEvent(
                new TransferStatusChangedEvent(request.getId(), fromStatus, toStatus, actor.getId()));

        // @Version 은 flush 시점에 올라간다. 밀어내지 않으면 증가 전 버전이 응답에 실려
        // 클라이언트가 다음 요청에서 매번 409 를 맞는다.
        requestRepository.flush();

        return TransitionResponse.of(request, actor);
    }

    /** 이송 요청 생성 */
    @Transactional
    public TransferCreateResponse create(TransferCreateRequest req, Long staffId) {
        // ... 재원/검사종류 조회 생략
        // TransferRequest.create(...) 호출 후 저장
        // 최초 이력(TransferEvent) 도 함께 적재한다: fromStatus = null, toStatus = REQUESTED
        return null; // 실제 구현 시 채움
    }
}
```

### 2-6. 알림 발송 (커밋 이후)

```java
package com.nursecollab.domain.transfer.event;

import com.nursecollab.infra.realtime.RealtimeNotifier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class TransferEventListener {

    private final RealtimeNotifier notifier;

    /**
     * AFTER_COMMIT 으로 지정하는 것이 핵심이다.
     * 트랜잭션이 롤백되면 이 메서드는 아예 실행되지 않는다.
     * 즉 "DB 에는 안 바뀌었는데 알림만 날아가는" 상황이 생기지 않는다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStatusChanged(TransferStatusChangedEvent event) {
        notifier.broadcastStatusChanged(event);
    }
}
```

```java
package com.nursecollab.infra.realtime;

import com.nursecollab.domain.transfer.event.TransferStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RealtimeNotifier {

    private final SimpMessagingTemplate messagingTemplate;

    /** 요청 파트와 수행 파트 양쪽 채널로 동시에 쏜다 */
    public void broadcastStatusChanged(TransferStatusChangedEvent event) {
        // 실제 구현에서는 requestId 로 상세를 조회해 페이로드를 만든다
        // messagingTemplate.convertAndSend("/topic/department/" + fromDeptId, payload);
        // messagingTemplate.convertAndSend("/topic/department/" + toDeptId,   payload);
    }
}
```

### 2-7. 컨트롤러

```java
package com.nursecollab.domain.transfer.controller;

import com.nursecollab.domain.transfer.dto.*;
import com.nursecollab.domain.transfer.service.TransferRequestService;
import com.nursecollab.global.security.LoginStaff;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/transfer-requests")
@RequiredArgsConstructor
public class TransferRequestController {

    private final TransferRequestService transferRequestService;

    /** 이송 요청 생성 */
    @PostMapping
    public ResponseEntity<TransferCreateResponse> create(
            @Valid @RequestBody TransferCreateRequest request,
            @AuthenticationPrincipal LoginStaff loginStaff) {

        TransferCreateResponse response =
                transferRequestService.create(request, loginStaff.staffId());
        return ResponseEntity
                .created(URI.create("/api/v1/transfer-requests/" + response.id()))
                .body(response);
    }

    /** 상태 전이 (접수/준비완료/이송중/보류/취소 전부 이 하나로 처리) */
    @PostMapping("/{id}/transitions")
    public ResponseEntity<TransitionResponse> transition(
            @PathVariable Long id,
            @Valid @RequestBody TransitionRequest request,
            @AuthenticationPrincipal LoginStaff loginStaff) {

        return ResponseEntity.ok(
                transferRequestService.transition(id, request, loginStaff.staffId()));
    }
}
```

### 2-8. DTO

```java
package com.nursecollab.domain.transfer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

/** 상태 전이 요청 */
public record TransitionRequest(
        @NotBlank(message = "변경할 상태는 필수입니다.")
        String toStatus,

        String reason,              // 보류/취소 시 필수 (검증은 도메인에서)
        OffsetDateTime scheduledAt, // 접수 시 필수

        @NotNull(message = "버전 정보는 필수입니다.")
        Long version
) {}
```

---

## 3. 에러 처리

```java
package com.nursecollab.global.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 에러코드 / HTTP 상태 / 사용자 노출 메시지를 한 곳에서 관리한다.
 * message 는 화면에 그대로 띄울 수 있는 한국어 문장으로 작성한다.
 */
@Getter
public enum ErrorCode {

    // 입력값
    INVALID_INPUT("VAL-001", HttpStatus.BAD_REQUEST, "입력값을 확인해 주세요."),

    // 인증
    INVALID_CREDENTIALS("AUTH-001", HttpStatus.UNAUTHORIZED,  "아이디 또는 비밀번호가 올바르지 않습니다."),
    TOKEN_EXPIRED      ("AUTH-002", HttpStatus.UNAUTHORIZED,  "로그인이 만료되었습니다. 다시 로그인해 주세요."),
    INACTIVE_ACCOUNT   ("AUTH-003", HttpStatus.UNAUTHORIZED,  "비활성화된 계정입니다. 관리자에게 문의하세요."),

    // 권한
    NOT_RELATED_DEPARTMENT("PERM-001", HttpStatus.FORBIDDEN, "해당 요청에 관여하는 파트가 아닙니다."),
    NOT_ALLOWED_ACTOR     ("PERM-002", HttpStatus.FORBIDDEN, "이 작업은 상대 파트에서 처리해야 합니다."),
    INSUFFICIENT_ROLE     ("PERM-003", HttpStatus.FORBIDDEN, "이 작업을 수행할 권한이 없습니다."),

    // 재원 / 검사
    ENCOUNTER_NOT_FOUND("ENC-000", HttpStatus.NOT_FOUND, "재원 정보를 찾을 수 없습니다."),
    DISCHARGED_ENCOUNTER("ENC-001", HttpStatus.UNPROCESSABLE_ENTITY, "퇴원한 환자에 대해서는 요청할 수 없습니다."),
    EXAM_TYPE_NOT_FOUND("EXM-001", HttpStatus.NOT_FOUND, "검사 종류를 찾을 수 없습니다."),

    // 이송 요청
    REQUEST_NOT_FOUND ("TR-000", HttpStatus.NOT_FOUND,  "요청을 찾을 수 없습니다."),
    INVALID_TRANSITION("TR-001", HttpStatus.CONFLICT,   "현재 상태에서는 변경할 수 없습니다. 화면을 새로고침해 주세요."),
    VERSION_CONFLICT  ("TR-002", HttpStatus.CONFLICT,   "다른 사용자가 먼저 처리했습니다. 화면을 새로고침해 주세요."),
    REASON_REQUIRED   ("TR-003", HttpStatus.BAD_REQUEST,"보류 또는 취소 시 사유는 필수입니다."),
    ALREADY_FINISHED  ("TR-004", HttpStatus.CONFLICT,   "이미 종료된 요청입니다."),
    SCHEDULE_REQUIRED ("TR-005", HttpStatus.BAD_REQUEST,"접수 시 예정 시각은 필수입니다."),

    // 간호기록
    NOTE_NOT_FOUND("NN-000", HttpStatus.NOT_FOUND, "간호기록을 찾을 수 없습니다."),
    NOTE_NOT_EDITABLE("NN-001", HttpStatus.FORBIDDEN, "본인이 작성한 기록만 수정할 수 있습니다."),
    NOTE_EDIT_WINDOW_CLOSED("NN-002", HttpStatus.UNPROCESSABLE_ENTITY, "작성 후 24시간이 지난 기록은 수정할 수 없습니다. 정정 기록을 새로 남겨 주세요."),
    NOTE_CONTENT_REQUIRED("NN-003", HttpStatus.BAD_REQUEST, "기록 내용은 비워 둘 수 없습니다."),

    // 기타
    STAFF_NOT_FOUND("STF-001", HttpStatus.NOT_FOUND, "직원 정보를 찾을 수 없습니다."),
    INTERNAL_ERROR ("SYS-001", HttpStatus.INTERNAL_SERVER_ERROR, "일시적인 오류가 발생했습니다.");

    private final String code;
    private final HttpStatus status;
    private final String message;

    ErrorCode(String code, HttpStatus status, String message) {
        this.code = code;
        this.status = status;
        this.message = message;
    }
}
```

```java
package com.nursecollab.global.error;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 우리가 의도적으로 던진 비즈니스 예외 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e,
                                                        HttpServletRequest req) {
        ErrorCode code = e.getErrorCode();
        return ResponseEntity.status(code.getStatus())
                .body(ErrorResponse.of(code, req.getRequestURI()));
    }

    /**
     * JPA 낙관적 락 충돌.
     * 서비스에서 버전을 먼저 검사하지만, 그 사이에 다른 트랜잭션이 커밋하면 여기로 온다.
     * 이중 방어선이다.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(HttpServletRequest req) {
        ErrorCode code = ErrorCode.VERSION_CONFLICT;
        return ResponseEntity.status(code.getStatus())
                .body(ErrorResponse.of(code, req.getRequestURI()));
    }

    /** @Valid 검증 실패 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e,
                                                          HttpServletRequest req) {
        List<ErrorResponse.FieldError> fieldErrors = e.getBindingResult()
                .getFieldErrors().stream()
                .map(fe -> new ErrorResponse.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();

        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(ErrorCode.INVALID_INPUT, req.getRequestURI(), fieldErrors));
    }

    /** 예상 못 한 오류 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e, HttpServletRequest req) {
        log.error("처리되지 않은 예외 발생. uri={}", req.getRequestURI(), e);
        ErrorCode code = ErrorCode.INTERNAL_ERROR;
        return ResponseEntity.status(code.getStatus())
                .body(ErrorResponse.of(code, req.getRequestURI()));
    }
}
```

---

## 4. 테스트 전략

상태 전이는 **단위 테스트로 전부 커버**할 수 있다. DB도 스프링 컨텍스트도 필요 없다.
포트폴리오에서 "테스트 짰다"를 증명하기 가장 좋은 지점이다.

```java
class TransferStatusTest {

    @Test
    void 검사실은_요청됨_상태를_접수할_수_있다() {
        var rule = TransferStatus.findRule(TransferStatus.REQUESTED, TransferStatus.ACCEPTED);

        assertThat(rule).isNotNull();
        assertThat(rule.actorSide()).isEqualTo(ActorSide.PERFORMER);
        assertThat(rule.scheduleRequired()).isTrue();   // 접수 시 예정시각 필수
    }

    @Test
    void 요청됨에서_바로_검사중으로는_갈_수_없다() {
        assertThat(TransferStatus.findRule(TransferStatus.REQUESTED, TransferStatus.IN_PROGRESS))
                .isNull();
    }

    @Test
    void 병동은_준비완료를_이송중으로만_바꿀_수_있다() {
        var available = TransferStatus.availableFor(TransferStatus.READY, ActorSide.REQUESTER);

        assertThat(available)
                .containsExactlyInAnyOrder(
                        TransferStatus.IN_TRANSIT,
                        TransferStatus.ON_HOLD,
                        TransferStatus.CANCELLED);
    }
}
```

통합 테스트는 **Testcontainers 로 진짜 PostgreSQL** 을 띄운다.
H2 를 쓰면 JSONB, 파티셔닝 같은 PostgreSQL 전용 문법에서 깨진다.

```java
@SpringBootTest
@Testcontainers
class TransferRequestServiceTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Test
    void 두_명이_동시에_접수하면_한_명은_실패한다() {
        // 스레드 2개로 동시에 transition 호출
        // 하나는 성공, 하나는 VERSION_CONFLICT 예외
    }
}
```

마지막 테스트는 **동시성 이슈를 실제로 재현**하는 것이라 면접에서 이야깃거리가 된다.
