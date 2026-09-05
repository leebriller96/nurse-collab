package com.nursecollab.domain.transfer.entity;

import java.util.Arrays;
import java.util.LinkedHashSet;
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
     * @param from             시작 상태
     * @param to               도착 상태
     * @param actorSide        이 전이를 누를 수 있는 쪽
     * @param reasonRequired   사유 입력이 필수인가
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
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public static TransferStatus from(String value) {
        return Arrays.stream(values())
                .filter(s -> s.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("알 수 없는 상태: " + value));
    }
}
