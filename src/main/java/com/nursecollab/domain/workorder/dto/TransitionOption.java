package com.nursecollab.domain.workorder.dto;

import com.nursecollab.domain.staff.entity.Staff;
import com.nursecollab.domain.workorder.entity.OrderStatus;
import com.nursecollab.domain.workorder.entity.WorkOrder;

import java.util.List;

/**
 * 지금 누를 수 있는 버튼 하나.
 *
 * 상태값만 내려보내던 것을 이름·행동·필수 입력까지 함께 내보내도록 바꿨다.
 * 종류마다 같은 상태를 다르게 부르고 요구하는 입력도 다른데, 화면이 그 표를
 * 따로 들고 있으면 종류를 더할 때 두 곳을 고쳐야 하고 한쪽만 고쳐지는 날이 온다.
 * 그날 화면은 사유 칸을 띄우지 않고, 누르는 사람은 왜 안 되는지 모른 채 버튼만 누른다.
 *
 * 상세 응답과 전이 응답이 똑같이 이 목록을 내려주므로 만드는 곳도 여기 하나다.
 *
 * @param label            상태의 이름 ("수리중")
 * @param actionLabel      버튼에 쓸 말 ("수리 시작")
 * @param reasonRequired   누르기 전에 사유를 받아야 하는가
 * @param scheduleRequired 누르기 전에 예정시각을 받아야 하는가
 */
public record TransitionOption(
        OrderStatus status,
        String label,
        String actionLabel,
        boolean reasonRequired,
        boolean scheduleRequired
) {
    public static List<TransitionOption> listOf(WorkOrder request, Staff viewer) {
        var type = request.getOrderType();
        OrderStatus current = request.getStatus();

        return request.availableTransitions(viewer).stream()
                .map(to -> {
                    // 보류에서 직전 상태로 돌아가는 것은 규칙표에 없다(도착 상태가 동적이다).
                    // 그때 버튼에 "접수" 라고 적으면 다시 접수하는 것처럼 보인다.
                    boolean releasingHold =
                            current == OrderStatus.ON_HOLD && to != OrderStatus.CANCELLED;

                    var rule = type.findRule(current, to);
                    return new TransitionOption(
                            to,
                            type.labelOf(to),
                            releasingHold ? "보류 해제" : type.actionLabelOf(to),
                            rule != null && rule.reasonRequired(),
                            rule != null && rule.scheduleRequired());
                })
                .toList();
    }
}
