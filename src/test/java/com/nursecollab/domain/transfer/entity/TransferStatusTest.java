package com.nursecollab.domain.transfer.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 전이 규칙표 자체에 대한 검증. DB 도 스프링 컨텍스트도 필요 없다. */
class TransferStatusTest {

    @Test
    void 검사실은_요청됨_상태를_접수할_수_있다() {
        var rule = TransferStatus.findRule(TransferStatus.REQUESTED, TransferStatus.ACCEPTED);

        assertThat(rule).isNotNull();
        assertThat(rule.actorSide()).isEqualTo(ActorSide.PERFORMER);
        assertThat(rule.scheduleRequired()).isTrue();
    }

    @Test
    void 요청됨에서_바로_검사중으로는_갈_수_없다() {
        assertThat(TransferStatus.findRule(TransferStatus.REQUESTED, TransferStatus.IN_PROGRESS))
                .isNull();
    }

    @Test
    void 완료된_요청은_어떤_상태로도_갈_수_없다() {
        for (TransferStatus to : TransferStatus.values()) {
            assertThat(TransferStatus.findRule(TransferStatus.COMPLETED, to))
                    .as("COMPLETED -> %s", to)
                    .isNull();
        }
    }

    @Test
    void 병동은_준비완료를_이송중_보류_취소로만_바꿀_수_있다() {
        var available = TransferStatus.availableFor(TransferStatus.READY, ActorSide.REQUESTER);

        assertThat(available).containsExactlyInAnyOrder(
                TransferStatus.IN_TRANSIT,
                TransferStatus.ON_HOLD,
                TransferStatus.CANCELLED);
    }

    @Test
    void 검사실은_준비완료를_이송중으로_바꿀_수_없다() {
        var available = TransferStatus.availableFor(TransferStatus.READY, ActorSide.PERFORMER);

        assertThat(available).doesNotContain(TransferStatus.IN_TRANSIT);
    }

    @Test
    void 완료와_취소만_종료_상태다() {
        assertThat(TransferStatus.COMPLETED.isTerminal()).isTrue();
        assertThat(TransferStatus.CANCELLED.isTerminal()).isTrue();

        assertThat(TransferStatus.ON_HOLD.isTerminal()).isFalse();
        assertThat(TransferStatus.REQUESTED.isTerminal()).isFalse();
        assertThat(TransferStatus.RETURNED.isTerminal()).isFalse();
    }

    @Test
    void 보류와_취소로_가는_전이는_모두_사유가_필수다() {
        for (TransferStatus from : TransferStatus.values()) {
            for (TransferStatus to : new TransferStatus[]{TransferStatus.ON_HOLD, TransferStatus.CANCELLED}) {
                var rule = TransferStatus.findRule(from, to);
                if (rule != null) {
                    assertThat(rule.reasonRequired())
                            .as("%s -> %s 는 사유가 필수여야 한다", from, to)
                            .isTrue();
                }
            }
        }
    }

    @Test
    void 예정시각이_필수인_전이는_접수뿐이다() {
        for (TransferStatus from : TransferStatus.values()) {
            for (TransferStatus to : TransferStatus.values()) {
                var rule = TransferStatus.findRule(from, to);
                if (rule != null && rule.scheduleRequired()) {
                    assertThat(to).isEqualTo(TransferStatus.ACCEPTED);
                }
            }
        }
    }

    @Test
    void 종료_상태에서는_누를_수_있는_것이_없다() {
        for (ActorSide side : ActorSide.values()) {
            assertThat(TransferStatus.availableFor(TransferStatus.COMPLETED, side)).isEmpty();
            assertThat(TransferStatus.availableFor(TransferStatus.CANCELLED, side)).isEmpty();
        }
    }

    @Test
    void 상태_이름은_대소문자를_가리지_않고_해석된다() {
        assertThat(TransferStatus.from("accepted")).isEqualTo(TransferStatus.ACCEPTED);
        assertThat(TransferStatus.from("IN_TRANSIT")).isEqualTo(TransferStatus.IN_TRANSIT);
    }
}
