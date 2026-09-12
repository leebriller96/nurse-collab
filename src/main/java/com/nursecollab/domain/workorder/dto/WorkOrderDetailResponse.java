package com.nursecollab.domain.workorder.dto;

import com.nursecollab.domain.department.dto.DepartmentSummary;
import com.nursecollab.domain.patient.dto.AlertResponse;
import com.nursecollab.domain.patient.dto.ChecklistWarning;
import com.nursecollab.domain.patient.entity.Sex;
import com.nursecollab.domain.staff.entity.Staff;
import com.nursecollab.domain.workorder.entity.OrderPriority;
import com.nursecollab.domain.workorder.entity.OrderStatus;
import com.nursecollab.domain.workorder.entity.OrderType;
import com.nursecollab.domain.workorder.entity.WorkOrder;

import java.time.OffsetDateTime;
import java.util.List;

/** 요청 상세(W-05 / E-02) */
public record WorkOrderDetailResponse(
        Long id,
        String requestNo,
        OrderType orderType,
        String orderTypeLabel,
        OrderStatus status,
        /** 이 종류에서 이 상태를 부르는 이름 (검사중 / 조제중 / 수리중) */
        String statusLabel,
        OrderPriority priority,
        /** 환자가 없는 업무에서는 encounter 와 patient 가 모두 비어 있다. */
        EncounterInfo encounter,
        PatientInfo patient,
        ServiceItemInfo serviceItem,
        DepartmentSummary fromDepartment,
        DepartmentSummary toDepartment,
        StaffInfo requestedBy,
        OffsetDateTime requestedAt,
        OffsetDateTime desiredAt,
        OffsetDateTime scheduledAt,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        String note,
        String holdReason,
        List<AlertResponse> alerts,
        List<ChecklistWarning> checklistWarnings,
        List<TransitionOption> availableTransitions,
        Long version
) {
    public record EncounterInfo(Long encounterId, String roomNo, String bedNo, boolean isMobile) {}

    public record PatientInfo(String patientNo, String name, int age, Sex sex) {}

    public record ServiceItemInfo(Long id, String code, String name, int defaultDuration,
                               String prepInstruction) {}

    public record StaffInfo(Long id, String name) {}

    public static WorkOrderDetailResponse of(WorkOrder request,
                                            Staff viewer,
                                            List<AlertResponse> alerts,
                                            List<ChecklistWarning> checklistWarnings) {
        var encounter = request.getEncounter();
        var serviceItem = request.getServiceItem();
        var type = request.getOrderType();

        EncounterInfo encounterInfo = null;
        PatientInfo patientInfo = null;
        if (encounter != null) {
            var patient = encounter.getPatient();
            encounterInfo = new EncounterInfo(encounter.getId(), encounter.getRoomNo(),
                    encounter.getBedNo(), encounter.isMobile());
            patientInfo = new PatientInfo(patient.getPatientNo(), patient.getName(),
                    patient.age(), patient.getSex());
        }

        List<TransitionOption> transitions = TransitionOption.listOf(request, viewer);

        return new WorkOrderDetailResponse(
                request.getId(),
                request.getRequestNo(),
                type,
                type.getLabel(),
                request.getStatus(),
                type.labelOf(request.getStatus()),
                request.getPriority(),
                encounterInfo,
                patientInfo,
                new ServiceItemInfo(serviceItem.getId(), serviceItem.getCode(), serviceItem.getName(),
                        serviceItem.getDefaultDuration(), serviceItem.getPrepInstruction()),
                DepartmentSummary.from(request.getFromDepartment()),
                DepartmentSummary.from(request.getToDepartment()),
                new StaffInfo(request.getRequestedBy().getId(), request.getRequestedBy().getName()),
                request.getRequestedAt(),
                request.getDesiredAt(),
                request.getScheduledAt(),
                request.getStartedAt(),
                request.getCompletedAt(),
                request.getNote(),
                request.getHoldReason(),
                alerts,
                checklistWarnings,
                transitions,
                request.getVersion());
    }
}
