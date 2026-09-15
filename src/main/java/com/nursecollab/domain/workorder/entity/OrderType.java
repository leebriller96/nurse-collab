package com.nursecollab.domain.workorder.entity;

import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.nursecollab.domain.workorder.entity.ActorSide.BOTH;
import static com.nursecollab.domain.workorder.entity.ActorSide.PERFORMER;
import static com.nursecollab.domain.workorder.entity.ActorSide.REQUESTER;
import static com.nursecollab.domain.workorder.entity.OrderStatus.ACCEPTED;
import static com.nursecollab.domain.workorder.entity.OrderStatus.AWAITING_PARTS;
import static com.nursecollab.domain.workorder.entity.OrderStatus.CANCELLED;
import static com.nursecollab.domain.workorder.entity.OrderStatus.COLLECTED;
import static com.nursecollab.domain.workorder.entity.OrderStatus.COMPLETED;
import static com.nursecollab.domain.workorder.entity.OrderStatus.DELIVERED;
import static com.nursecollab.domain.workorder.entity.OrderStatus.DISPENSED;
import static com.nursecollab.domain.workorder.entity.OrderStatus.IN_PROGRESS;
import static com.nursecollab.domain.workorder.entity.OrderStatus.IN_TRANSIT;
import static com.nursecollab.domain.workorder.entity.OrderStatus.ON_HOLD;
import static com.nursecollab.domain.workorder.entity.OrderStatus.READY;
import static com.nursecollab.domain.workorder.entity.OrderStatus.REQUESTED;
import static com.nursecollab.domain.workorder.entity.OrderStatus.RESULTED;
import static com.nursecollab.domain.workorder.entity.OrderStatus.RETURNED;

/**
 * 업무 종류. 종류마다 흐름이 다르다.
 *
 * 전이 규칙은 이 안에만 있다. 서비스나 컨트롤러에 상태 분기를 만들지 않는다.
 * 이송 하나였을 때는 규칙표가 OrderStatus 에 있었는데, 종류가 늘면서 이리로 옮겼다.
 * 옮긴 것은 위치일 뿐이고 "규칙이 사는 곳은 한 군데" 라는 원칙은 그대로다.
 *
 * 종류를 더할 때 고칠 곳은 아래 표 하나다.
 * 화면의 버튼 목록도, 사유·예정시각 필수 여부도 전부 여기서 나온다.
 */
public enum OrderType {

    /** 환자를 검사실로 보내고 데려온다. 이 시스템이 처음 만들어진 이유다. */
    TRANSFER("이송", "TR", true),

    /** 병동이 채취하고 진단검사의학과가 분석한다. 환자는 움직이지 않는다. */
    SPECIMEN("검체", "SP", true),

    /** 약제부가 조제해서 병동으로 올려보낸다. */
    PHARMACY("약제", "PH", true),

    /**
     * 의공학팀이 장비를 고친다.
     * 이 종류에는 환자가 없다. 수액펌프가 고장 난 것은 누구의 진료도 아니다.
     */
    EQUIPMENT("의공", "EQ", false);

    private final String label;
    private final String requestNoPrefix;
    private final boolean patientRequired;

    OrderType(String label, String requestNoPrefix, boolean patientRequired) {
        this.label = label;
        this.requestNoPrefix = requestNoPrefix;
        this.patientRequired = patientRequired;
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
            OrderStatus from,
            OrderStatus to,
            ActorSide actorSide,
            boolean reasonRequired,
            boolean scheduleRequired
    ) {}

    // ------------------------------------------------------------------
    // 전이 규칙표
    //
    // ON_HOLD 에서 원래 상태로 복귀하는 것은 도착 상태가 동적이라 표에 없다.
    // WorkOrder.transitionTo() 가 저장된 직전 상태를 보고 따로 처리한다.
    // ------------------------------------------------------------------
    private static final Map<OrderType, List<Rule>> RULES = new EnumMap<>(OrderType.class);

    /** 종류가 달리 부르는 상태 이름. 없으면 OrderStatus 의 기본 이름을 쓴다. */
    private static final Map<OrderType, Map<OrderStatus, String>> LABELS =
            new EnumMap<>(OrderType.class);

    /**
     * 버튼에 쓰는 표현.
     *
     * 상태명은 "이미 그렇게 된 것" 을 가리키는 말이라 버튼에 그대로 쓰면 어색하다.
     * "복귀중" 이라고 적힌 버튼을 누르라고 하면 무슨 뜻인지 한 번 더 생각해야 한다.
     * 간호사가 실제로 하는 행동으로 적는다.
     *
     * 화면이 아니라 여기 두는 이유는 이 표가 종류마다 다르기 때문이다.
     * 같은 IN_PROGRESS 로 가는 버튼이 이송에서는 "검사 시작", 의공에서는 "수리 시작" 이다.
     */
    private static final Map<OrderType, Map<OrderStatus, String>> ACTIONS =
            new EnumMap<>(OrderType.class);

    /** 어느 종류에나 있는 두 가지. 종류별 표에 매번 다시 적지 않는다. */
    private static final Map<OrderStatus, String> COMMON_ACTIONS =
            Map.of(ON_HOLD, "보류", CANCELLED, "취소");

    static {
        // ── 이송 ────────────────────────────────────────────────
        // 병동이 데려가고 데려온다. 검사실은 받고 검사한다.
        RULES.put(TRANSFER, List.of(
                new Rule(REQUESTED,   ACCEPTED,    PERFORMER, false, true),
                new Rule(REQUESTED,   ON_HOLD,     PERFORMER, true,  false),
                new Rule(REQUESTED,   CANCELLED,   BOTH,      true,  false),

                new Rule(ACCEPTED,    READY,       PERFORMER, false, false),
                new Rule(ACCEPTED,    ON_HOLD,     PERFORMER, true,  false),
                new Rule(ACCEPTED,    CANCELLED,   BOTH,      true,  false),

                new Rule(READY,       IN_TRANSIT,  REQUESTER, false, false),
                new Rule(READY,       ON_HOLD,     BOTH,      true,  false),
                new Rule(READY,       CANCELLED,   BOTH,      true,  false),

                new Rule(IN_TRANSIT,  IN_PROGRESS, PERFORMER, false, false),
                new Rule(IN_TRANSIT,  ON_HOLD,     BOTH,      true,  false),

                new Rule(IN_PROGRESS, RETURNED,    PERFORMER, false, false),

                new Rule(RETURNED,    COMPLETED,   REQUESTER, false, false),

                new Rule(ON_HOLD,     CANCELLED,   BOTH,      true,  false)
        ));
        LABELS.put(TRANSFER, Map.of(IN_PROGRESS, "검사중"));
        ACTIONS.put(TRANSFER, Map.of(
                ACCEPTED,    "접수",
                READY,       "준비 완료",
                IN_TRANSIT,  "환자 출발",
                IN_PROGRESS, "검사 시작",
                RETURNED,    "검사 종료",
                COMPLETED,   "병동 도착"));

        // ── 검체 ────────────────────────────────────────────────
        // 환자가 움직이지 않으므로 준비완료·이송중·복귀중이 없다.
        // 채취는 병동이 한다. 그래서 접수됨 → 채취완료는 요청자 쪽이 누른다.
        // 예정시각도 받지 않는다. 검체는 도착한 순서대로 돌린다.
        RULES.put(SPECIMEN, List.of(
                new Rule(REQUESTED,   ACCEPTED,    PERFORMER, false, false),
                new Rule(REQUESTED,   ON_HOLD,     PERFORMER, true,  false),
                new Rule(REQUESTED,   CANCELLED,   BOTH,      true,  false),

                new Rule(ACCEPTED,    COLLECTED,   REQUESTER, false, false),
                new Rule(ACCEPTED,    ON_HOLD,     BOTH,      true,  false),
                new Rule(ACCEPTED,    CANCELLED,   BOTH,      true,  false),

                new Rule(COLLECTED,   IN_PROGRESS, PERFORMER, false, false),
                new Rule(COLLECTED,   ON_HOLD,     BOTH,      true,  false),

                new Rule(IN_PROGRESS, RESULTED,    PERFORMER, false, false),

                // 결과를 등록했다고 끝이 아니다. 병동이 보고 나서 닫는다.
                new Rule(RESULTED,    COMPLETED,   REQUESTER, false, false),

                new Rule(ON_HOLD,     CANCELLED,   BOTH,      true,  false)
        ));
        LABELS.put(SPECIMEN, Map.of(IN_PROGRESS, "검사중"));
        ACTIONS.put(SPECIMEN, Map.of(
                ACCEPTED,    "접수",
                COLLECTED,   "채취 완료",
                IN_PROGRESS, "검사 시작",
                RESULTED,    "결과 등록",
                COMPLETED,   "결과 확인"));

        // ── 약제 ────────────────────────────────────────────────
        // 조제하고 불출하면 병동이 받았는지 확인해서 닫는다.
        // 약이 올라왔는지 아닌지는 약제부가 알 수 없다.
        RULES.put(PHARMACY, List.of(
                new Rule(REQUESTED,   ACCEPTED,    PERFORMER, false, false),
                new Rule(REQUESTED,   ON_HOLD,     PERFORMER, true,  false),
                new Rule(REQUESTED,   CANCELLED,   BOTH,      true,  false),

                new Rule(ACCEPTED,    IN_PROGRESS, PERFORMER, false, false),
                new Rule(ACCEPTED,    ON_HOLD,     BOTH,      true,  false),
                new Rule(ACCEPTED,    CANCELLED,   BOTH,      true,  false),

                new Rule(IN_PROGRESS, DISPENSED,   PERFORMER, false, false),
                new Rule(IN_PROGRESS, ON_HOLD,     BOTH,      true,  false),

                new Rule(DISPENSED,   DELIVERED,   PERFORMER, false, false),

                new Rule(DELIVERED,   COMPLETED,   REQUESTER, false, false),

                new Rule(ON_HOLD,     CANCELLED,   BOTH,      true,  false)
        ));
        LABELS.put(PHARMACY, Map.of(IN_PROGRESS, "조제중"));
        ACTIONS.put(PHARMACY, Map.of(
                ACCEPTED,    "접수",
                IN_PROGRESS, "조제 시작",
                DISPENSED,   "조제 완료",
                DELIVERED,   "불출",
                COMPLETED,   "수령 확인"));

        // ── 의공 ────────────────────────────────────────────────
        // 고친 사람이 끝냈다고 말한다. 병동의 확인을 기다리지 않는 유일한 종류다.
        // 부품대기는 보류와 다르다. 보류는 사람이 풀 때까지 멈춘 것이고
        // 부품대기는 진행중인데 물건을 기다리는 것이라, 지연으로 세면 안 된다.
        RULES.put(EQUIPMENT, List.of(
                new Rule(REQUESTED,      ACCEPTED,       PERFORMER, false, false),
                new Rule(REQUESTED,      CANCELLED,      BOTH,      true,  false),

                new Rule(ACCEPTED,       IN_PROGRESS,    PERFORMER, false, false),
                new Rule(ACCEPTED,       ON_HOLD,        PERFORMER, true,  false),
                new Rule(ACCEPTED,       CANCELLED,      BOTH,      true,  false),

                // 어떤 부품을 기다리는지 적지 않으면 언제 끝날지 아무도 모른다.
                new Rule(IN_PROGRESS,    AWAITING_PARTS, PERFORMER, true,  false),
                new Rule(IN_PROGRESS,    COMPLETED,      PERFORMER, false, false),
                new Rule(IN_PROGRESS,    ON_HOLD,        PERFORMER, true,  false),

                new Rule(AWAITING_PARTS, IN_PROGRESS,    PERFORMER, false, false),
                new Rule(AWAITING_PARTS, CANCELLED,      BOTH,      true,  false),

                new Rule(ON_HOLD,        CANCELLED,      BOTH,      true,  false)
        ));
        LABELS.put(EQUIPMENT, Map.of(IN_PROGRESS, "수리중"));
        ACTIONS.put(EQUIPMENT, Map.of(
                ACCEPTED,       "접수",
                IN_PROGRESS,    "수리 시작",
                AWAITING_PARTS, "부품 대기",
                COMPLETED,      "수리 완료"));
    }

    // ------------------------------------------------------------------
    // 조회
    // ------------------------------------------------------------------

    public String getLabel() {
        return label;
    }

    /** 요청번호 앞에 붙는다. 번호만 보고 어떤 업무인지 알 수 있게 한다. */
    public String getRequestNoPrefix() {
        return requestNoPrefix;
    }

    /** 대상 환자가 있어야 하는 업무인가. 장비 수리만 아니다. */
    public boolean isPatientRequired() {
        return patientRequired;
    }

    public List<Rule> rules() {
        return RULES.get(this);
    }

    /** 이 종류에서 from 에서 to 로 가는 규칙. 없으면 허용되지 않는 전이다. */
    public Rule findRule(OrderStatus from, OrderStatus to) {
        return rules().stream()
                .filter(r -> r.from() == from && r.to() == to)
                .findFirst()
                .orElse(null);
    }

    /**
     * 특정 상태에서 특정 행위자가 누를 수 있는 상태 목록.
     * API 응답의 availableTransitions 가 이 메서드 결과다.
     */
    public Set<OrderStatus> availableFor(OrderStatus current, ActorSide side) {
        return rules().stream()
                .filter(r -> r.from() == current)
                .filter(r -> r.actorSide() == BOTH || r.actorSide() == side)
                .map(Rule::to)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** 이 종류가 실제로 쓰는 상태 전부. 화면이 진행 단계를 그릴 때 쓴다. */
    public Set<OrderStatus> statuses() {
        Set<OrderStatus> used = new LinkedHashSet<>();
        used.add(REQUESTED);
        for (Rule r : rules()) {
            used.add(r.from());
            used.add(r.to());
        }
        return used;
    }

    /**
     * 이 종류에서 이 상태를 뭐라고 부르는지.
     * 같은 IN_PROGRESS 라도 검사실은 "검사중", 약제부는 "조제중", 의공학팀은 "수리중" 이다.
     */
    public String labelOf(OrderStatus status) {
        return LABELS.getOrDefault(this, Map.of())
                .getOrDefault(status, status.getLabel());
    }

    /**
     * 이 상태로 가는 버튼에 쓸 말.
     * 따로 정한 것이 없으면 상태 이름을 그대로 쓴다.
     */
    public String actionLabelOf(OrderStatus status) {
        String common = COMMON_ACTIONS.get(status);
        if (common != null) return common;

        return ACTIONS.getOrDefault(this, Map.of())
                .getOrDefault(status, labelOf(status));
    }

    public static OrderType from(String value) {
        for (OrderType t : values()) {
            if (t.name().equalsIgnoreCase(value)) return t;
        }
        throw new IllegalArgumentException("알 수 없는 업무 종류: " + value);
    }
}
