package com.nursecollab.domain.workorder.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 종류별 전이 규칙표 자체에 대한 검증. DB 도 스프링 컨텍스트도 필요 없다.
 *
 * 여기 있는 검사 중 절반은 특정 종류가 아니라 <b>모든 종류</b>에 건다.
 * 종류를 새로 더할 때 규칙표만 적으면 이 불변식들이 저절로 따라붙게 하려는 것이다.
 * 종류마다 테스트를 손으로 다시 쓰게 만들면 언젠가 하나를 빼먹는다.
 */
class OrderTypeTest {

    // ------------------------------------------------------------------
    // 이송 — 일반화 전과 똑같이 동작해야 한다
    // ------------------------------------------------------------------

    @Test
    void 검사실은_요청됨_상태를_접수할_수_있다() {
        var rule = OrderType.TRANSFER.findRule(OrderStatus.REQUESTED, OrderStatus.ACCEPTED);

        assertThat(rule).isNotNull();
        assertThat(rule.actorSide()).isEqualTo(ActorSide.PERFORMER);
        assertThat(rule.scheduleRequired()).isTrue();
    }

    @Test
    void 요청됨에서_바로_검사중으로는_갈_수_없다() {
        assertThat(OrderType.TRANSFER.findRule(OrderStatus.REQUESTED, OrderStatus.IN_PROGRESS))
                .isNull();
    }

    @Test
    void 병동은_준비완료를_이송중_보류_취소로만_바꿀_수_있다() {
        var available = OrderType.TRANSFER.availableFor(OrderStatus.READY, ActorSide.REQUESTER);

        assertThat(available).containsExactlyInAnyOrder(
                OrderStatus.IN_TRANSIT,
                OrderStatus.ON_HOLD,
                OrderStatus.CANCELLED);
    }

    @Test
    void 검사실은_준비완료를_이송중으로_바꿀_수_없다() {
        var available = OrderType.TRANSFER.availableFor(OrderStatus.READY, ActorSide.PERFORMER);

        assertThat(available).doesNotContain(OrderStatus.IN_TRANSIT);
    }

    // ------------------------------------------------------------------
    // 종류마다 흐름이 실제로 다르다
    // ------------------------------------------------------------------

    @Test
    void 검체는_환자를_옮기지_않으므로_이송_상태를_쓰지_않는다() {
        assertThat(OrderType.SPECIMEN.statuses())
                .doesNotContain(OrderStatus.READY, OrderStatus.IN_TRANSIT, OrderStatus.RETURNED);
    }

    @Test
    void 검체_채취는_검사실이_아니라_병동이_한다() {
        var rule = OrderType.SPECIMEN.findRule(OrderStatus.ACCEPTED, OrderStatus.COLLECTED);

        assertThat(rule).isNotNull();
        assertThat(rule.actorSide()).isEqualTo(ActorSide.REQUESTER);
    }

    @Test
    void 장비_수리는_대상_환자가_없다() {
        assertThat(OrderType.EQUIPMENT.isPatientRequired()).isFalse();

        // 나머지는 전부 환자가 있어야 한다
        assertThat(OrderType.TRANSFER.isPatientRequired()).isTrue();
        assertThat(OrderType.SPECIMEN.isPatientRequired()).isTrue();
        assertThat(OrderType.PHARMACY.isPatientRequired()).isTrue();
    }

    @Test
    void 장비_수리만_수행_파트가_스스로_끝낸다() {
        // 고쳤는지 아닌지는 고친 사람이 안다. 병동의 확인을 기다리지 않는다.
        var repair = OrderType.EQUIPMENT.findRule(OrderStatus.IN_PROGRESS, OrderStatus.COMPLETED);
        assertThat(repair).isNotNull();
        assertThat(repair.actorSide()).isEqualTo(ActorSide.PERFORMER);

        // 나머지 종류는 요청한 쪽이 확인하고 닫는다
        for (OrderType type : new OrderType[]{
                OrderType.TRANSFER, OrderType.SPECIMEN, OrderType.PHARMACY}) {
            for (OrderType.Rule r : type.rules()) {
                if (r.to() == OrderStatus.COMPLETED) {
                    assertThat(r.actorSide())
                            .as("%s 의 완료는 요청한 쪽이 눌러야 한다", type)
                            .isEqualTo(ActorSide.REQUESTER);
                }
            }
        }
    }

    @Test
    void 부품대기는_다시_수리중으로_돌아갈_수_있다() {
        // 보류와 달리 부품대기는 진행중의 한 모습이다. 되돌아갈 길이 없으면
        // 부품이 와도 상태를 못 바꾼다.
        assertThat(OrderType.EQUIPMENT.findRule(OrderStatus.AWAITING_PARTS, OrderStatus.IN_PROGRESS))
                .isNotNull();
    }

    @Test
    void 같은_상태라도_종류마다_부르는_이름이_다르다() {
        assertThat(OrderType.TRANSFER.labelOf(OrderStatus.IN_PROGRESS)).isEqualTo("검사중");
        assertThat(OrderType.PHARMACY.labelOf(OrderStatus.IN_PROGRESS)).isEqualTo("조제중");
        assertThat(OrderType.EQUIPMENT.labelOf(OrderStatus.IN_PROGRESS)).isEqualTo("수리중");

        // 따로 정하지 않은 상태는 기본 이름을 쓴다
        assertThat(OrderType.EQUIPMENT.labelOf(OrderStatus.ON_HOLD)).isEqualTo("보류");
    }

    @Test
    void 요청번호_접두사는_종류마다_다르다() {
        var prefixes = new LinkedHashSet<String>();
        for (OrderType type : OrderType.values()) {
            prefixes.add(type.getRequestNoPrefix());
        }
        assertThat(prefixes).hasSize(OrderType.values().length);
    }

    @Test
    void 다음_걸음을_누를_쪽이_차례다() {
        // 채취는 병동이 한다. 접수된 검체는 병동 차례다.
        assertThat(OrderType.SPECIMEN.isTurnOf(OrderStatus.ACCEPTED, ActorSide.REQUESTER)).isTrue();
        assertThat(OrderType.SPECIMEN.isTurnOf(OrderStatus.ACCEPTED, ActorSide.PERFORMER)).isFalse();
        // 불출된 약은 병동이 수령 확인을 눌러야 닫힌다. 교대 때 제일 많이 남는 것이 이것이다.
        assertThat(OrderType.PHARMACY.isTurnOf(OrderStatus.DELIVERED, ActorSide.REQUESTER)).isTrue();
        assertThat(OrderType.TRANSFER.isTurnOf(OrderStatus.READY, ActorSide.REQUESTER)).isTrue();
        assertThat(OrderType.EQUIPMENT.isTurnOf(OrderStatus.AWAITING_PARTS, ActorSide.PERFORMER)).isTrue();
    }

    @Test
    void 보류와_취소는_누를_수_있어도_차례가_아니다() {
        // 요청됨에서 병동이 누를 수 있는 것은 취소뿐이다. 그걸로 "병동 차례" 라고 하면
        // 접수를 기다리는 요청이 전부 병동이 멈춰 세운 것처럼 보인다.
        assertThat(OrderType.TRANSFER.availableFor(OrderStatus.REQUESTED, ActorSide.REQUESTER))
                .containsExactly(OrderStatus.CANCELLED);
        assertThat(OrderType.TRANSFER.isTurnOf(OrderStatus.REQUESTED, ActorSide.REQUESTER)).isFalse();
    }

    // ------------------------------------------------------------------
    // 모든 종류가 지켜야 하는 것
    // ------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(OrderType.class)
    void 안_끝난_상태에는_반드시_한쪽의_차례가_있다(OrderType type) {
        // 양쪽 다 "상대 차례" 라고 보는 상태가 생기면 그 요청은 두 목록 모두에서 기다리는 줄에 선다.
        // 인수인계 때 아무도 자기 일로 넘기지 않는다. 보류는 사람이 풀 때까지 멈춘 것이라 뺀다.
        for (OrderStatus status : type.statuses()) {
            if (status.isTerminal() || status == OrderStatus.ON_HOLD) continue;

            boolean requester = type.isTurnOf(status, ActorSide.REQUESTER);
            boolean performer = type.isTurnOf(status, ActorSide.PERFORMER);
            assertThat(requester || performer)
                    .as("%s: %s 는 아무의 차례도 아니다", type, status)
                    .isTrue();
            assertThat(requester && performer)
                    .as("%s: %s 가 양쪽 모두의 차례다", type, status)
                    .isFalse();
        }
    }

    @ParameterizedTest
    @EnumSource(OrderType.class)
    void 보류와_취소로_가는_전이는_모두_사유가_필수다(OrderType type) {
        for (OrderType.Rule rule : type.rules()) {
            if (rule.to() == OrderStatus.ON_HOLD || rule.to() == OrderStatus.CANCELLED) {
                assertThat(rule.reasonRequired())
                        .as("%s: %s -> %s 는 사유가 필수여야 한다", type, rule.from(), rule.to())
                        .isTrue();
            }
        }
    }

    @ParameterizedTest
    @EnumSource(OrderType.class)
    void 종료된_요청은_어떤_상태로도_갈_수_없다(OrderType type) {
        for (OrderType.Rule rule : type.rules()) {
            assertThat(rule.from().isTerminal())
                    .as("%s: 종료 상태 %s 에서 나가는 규칙이 있다", type, rule.from())
                    .isFalse();
        }
        for (ActorSide side : ActorSide.values()) {
            assertThat(type.availableFor(OrderStatus.COMPLETED, side)).isEmpty();
            assertThat(type.availableFor(OrderStatus.CANCELLED, side)).isEmpty();
        }
    }

    @ParameterizedTest
    @EnumSource(OrderType.class)
    void 예정시각이_필수인_전이는_접수뿐이다(OrderType type) {
        for (OrderType.Rule rule : type.rules()) {
            if (rule.scheduleRequired()) {
                assertThat(rule.to())
                        .as("%s: %s -> %s 가 예정시각을 요구한다", type, rule.from(), rule.to())
                        .isEqualTo(OrderStatus.ACCEPTED);
            }
        }
    }

    @ParameterizedTest
    @EnumSource(OrderType.class)
    void 같은_전이를_두_번_적지_않는다(OrderType type) {
        // 같은 from 에서 같은 to 로 가는 규칙이 둘이면 findRule 이 앞의 것만 보게 되어,
        // 나중에 적은 규칙(사유 필수 같은 것)이 조용히 무시된다.
        Set<String> seen = new HashSet<>();
        for (OrderType.Rule rule : type.rules()) {
            assertThat(seen.add(rule.from() + "->" + rule.to()))
                    .as("%s: %s -> %s 가 두 번 적혀 있다", type, rule.from(), rule.to())
                    .isTrue();
        }
    }

    @ParameterizedTest
    @EnumSource(OrderType.class)
    void 어느_종류든_요청됨에서_완료까지_갈_길이_있다(OrderType type) {
        // 규칙을 고치다가 중간 한 줄을 빠뜨리면 끝낼 수 없는 요청이 생긴다.
        // 화면에는 버튼이 하나도 없고, 그 요청은 영원히 목록에 남는다.
        assertThat(reachableFrom(type, OrderStatus.REQUESTED))
                .as("%s: 요청됨에서 완료에 닿지 못한다", type)
                .contains(OrderStatus.COMPLETED);
    }

    @ParameterizedTest
    @EnumSource(OrderType.class)
    void 어느_종류든_모든_상태에서_끝낼_길이_있다(OrderType type) {
        // 완료로 못 가더라도 최소한 취소는 할 수 있어야 한다.
        // 막다른 상태가 하나라도 있으면 그 상태에 빠진 요청은 손쓸 방법이 없다.
        for (OrderStatus status : type.statuses()) {
            if (status.isTerminal()) continue;

            assertThat(reachableFrom(type, status))
                    .as("%s: %s 에서 빠져나갈 길이 없다", type, status)
                    .containsAnyOf(OrderStatus.COMPLETED, OrderStatus.CANCELLED);
        }
    }

    /** start 에서 규칙표를 따라 닿을 수 있는 모든 상태 */
    private static Set<OrderStatus> reachableFrom(OrderType type, OrderStatus start) {
        Set<OrderStatus> seen = new LinkedHashSet<>();
        Deque<OrderStatus> todo = new ArrayDeque<>();
        todo.add(start);

        while (!todo.isEmpty()) {
            OrderStatus current = todo.poll();
            for (OrderType.Rule rule : type.rules()) {
                if (rule.from() == current && seen.add(rule.to())) {
                    todo.add(rule.to());
                }
            }
        }
        return seen;
    }
}
