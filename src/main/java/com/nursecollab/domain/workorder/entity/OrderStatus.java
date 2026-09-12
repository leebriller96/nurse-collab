package com.nursecollab.domain.workorder.entity;

import java.util.Arrays;
import java.util.Set;

/**
 * 업무 요청의 상태.
 *
 * 여기에는 "어떤 상태가 있는가" 만 있다. **어떤 종류가 어떤 상태를 쓰고,
 * 어디서 어디로 갈 수 있는가는 {@link OrderType} 이 가진다.**
 * 종류마다 흐름이 다르기 때문이다. 검체는 이송하지 않고, 장비 수리는 환자가 없다.
 *
 * 상태 목록을 종류별로 쪼개지 않고 한 곳에 모아 둔 이유:
 * 상태는 DB 에 문자열 한 컬럼으로 저장되고 감사 로그와 통계가 그 값을 그대로 읽는다.
 * 종류별로 같은 이름을 따로 두면 "IN_PROGRESS" 가 어느 enum 의 것인지 알 수 없어진다.
 */
public enum OrderStatus {

    REQUESTED("요청됨"),
    ACCEPTED("접수됨"),

    // 이송
    READY("준비완료"),
    IN_TRANSIT("이송중"),
    RETURNED("복귀중"),

    // 여러 종류가 함께 쓴다. 종류마다 부르는 이름이 달라서 OrderType 이 덮어쓴다
    // (검사중 / 조제중 / 수리중).
    IN_PROGRESS("진행중"),

    // 검체
    COLLECTED("채취완료"),
    RESULTED("결과등록"),

    // 약제
    DISPENSED("조제완료"),
    DELIVERED("불출완료"),

    // 의공
    AWAITING_PARTS("부품대기"),

    // 모든 종류가 함께 쓴다
    COMPLETED("완료"),
    ON_HOLD("보류"),
    CANCELLED("취소");

    private final String label;

    OrderStatus(String label) {
        this.label = label;
    }

    /**
     * 종류와 무관한 기본 이름.
     * 종류가 달리 부르는 것이 있으면 {@link OrderType#labelOf(OrderStatus)} 가 답한다.
     */
    public String getLabel() {
        return label;
    }

    /** 더 이상 상태가 바뀌지 않는 종료 상태인가 */
    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED;
    }

    /** 진행중 여부를 쿼리로 거를 때 쓴다 */
    public static Set<OrderStatus> terminals() {
        return Set.of(COMPLETED, CANCELLED);
    }

    public static OrderStatus from(String value) {
        return Arrays.stream(values())
                .filter(s -> s.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("알 수 없는 상태: " + value));
    }
}
