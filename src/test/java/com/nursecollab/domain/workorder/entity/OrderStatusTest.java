package com.nursecollab.domain.workorder.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 상태값 자체에 대한 검증.
 * 어디서 어디로 갈 수 있는가는 종류가 정하므로 {@link OrderTypeTest} 에 있다.
 */
class OrderStatusTest {

    @Test
    void 완료와_취소만_종료_상태다() {
        assertThat(OrderStatus.COMPLETED.isTerminal()).isTrue();
        assertThat(OrderStatus.CANCELLED.isTerminal()).isTrue();

        assertThat(OrderStatus.ON_HOLD.isTerminal()).isFalse();
        assertThat(OrderStatus.REQUESTED.isTerminal()).isFalse();
        assertThat(OrderStatus.RETURNED.isTerminal()).isFalse();
        assertThat(OrderStatus.AWAITING_PARTS.isTerminal()).isFalse();
    }

    @Test
    void 상태_이름은_대소문자를_가리지_않고_해석된다() {
        assertThat(OrderStatus.from("accepted")).isEqualTo(OrderStatus.ACCEPTED);
        assertThat(OrderStatus.from("IN_TRANSIT")).isEqualTo(OrderStatus.IN_TRANSIT);
    }

    @Test
    void 어떤_종류도_쓰지_않는_상태는_없다() {
        // 쓰이지 않는 상태가 남아 있으면 화면과 통계가 그 값을 계속 처리하려 든다.
        for (OrderStatus status : OrderStatus.values()) {
            boolean usedSomewhere = false;
            for (OrderType type : OrderType.values()) {
                if (type.statuses().contains(status)) {
                    usedSomewhere = true;
                    break;
                }
            }
            assertThat(usedSomewhere)
                    .as("%s 는 어느 종류에서도 쓰이지 않는다", status)
                    .isTrue();
        }
    }
}
