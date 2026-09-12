package com.nursecollab.domain.workorder.dto;

import com.nursecollab.domain.department.dto.DepartmentSummary;
import com.nursecollab.domain.patient.entity.Sex;
import com.nursecollab.domain.workorder.entity.OrderPriority;
import com.nursecollab.domain.workorder.entity.OrderStatus;
import com.nursecollab.domain.workorder.entity.OrderType;
import com.nursecollab.domain.workorder.entity.WorkOrder;

import java.time.Duration;
import java.time.OffsetDateTime;

/** 큐(E-01)와 현황(W-04)의 행 하나 */
public record WorkOrderSummary(
        Long id,
        String requestNo,
        OrderType orderType,
        OrderStatus status,
        /**
         * 이 종류에서 이 상태를 부르는 이름.
         * 같은 IN_PROGRESS 라도 검사실은 "검사중", 약제부는 "조제중" 이다.
         * 화면이 다시 계산하게 하면 규칙이 서버와 화면 두 군데로 갈라진다.
         */
        String statusLabel,
        OrderPriority priority,
        /** 장비 수리처럼 환자가 없는 업무에서는 비어 있다. */
        PatientInfo patient,
        String roomNo,
        String itemName,
        DepartmentSummary counterpartDepartment,
        OffsetDateTime requestedAt,
        OffsetDateTime scheduledAt,
        long waitingMinutes,
        int criticalAlertCount,
        Long version
) {
    public record PatientInfo(String patientNo, String name, int age, Sex sex) {}

    /**
     * @param inbound     우리 파트가 수행측이면 true. 상대 파트를 고르는 데 쓴다.
     * @param criticalAlertCount 중대 주의사항 수. 큐에서 빨간 표시의 근거다.
     */
    public static WorkOrderSummary of(WorkOrder request, boolean inbound, int criticalAlertCount) {
        var encounter = request.getEncounter();
        var counterpart = inbound ? request.getFromDepartment() : request.getToDepartment();

        PatientInfo patientInfo = null;
        if (encounter != null) {
            var patient = encounter.getPatient();
            patientInfo = new PatientInfo(patient.getPatientNo(), patient.getName(),
                    patient.age(), patient.getSex());
        }

        return new WorkOrderSummary(
                request.getId(),
                request.getRequestNo(),
                request.getOrderType(),
                request.getStatus(),
                request.getOrderType().labelOf(request.getStatus()),
                request.getPriority(),
                patientInfo,
                encounter == null ? null : encounter.getRoomNo(),
                request.getServiceItem().getName(),
                DepartmentSummary.from(counterpart),
                request.getRequestedAt(),
                request.getScheduledAt(),
                waitingMinutes(request),
                criticalAlertCount,
                request.getVersion());
    }

    /**
     * 요청 시각부터 흐른 시간. 진행중이면 지금까지, 끝났으면 완료 시각까지 센다.
     * 검사실 큐에서 오래 기다린 행을 진하게 칠하는 근거라서 "지금 기준" 이어야 한다.
     */
    private static long waitingMinutes(WorkOrder request) {
        OffsetDateTime end = request.getStatus().isTerminal() && request.getCompletedAt() != null
                ? request.getCompletedAt()
                : OffsetDateTime.now();
        return Math.max(0, Duration.between(request.getRequestedAt(), end).toMinutes());
    }
}
