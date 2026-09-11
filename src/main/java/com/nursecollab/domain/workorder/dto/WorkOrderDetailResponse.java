package com.nursecollab.domain.workorder.dto;

import com.nursecollab.domain.department.dto.DepartmentSummary;
import com.nursecollab.domain.patient.dto.AlertResponse;
import com.nursecollab.domain.patient.dto.ChecklistWarning;
import com.nursecollab.domain.patient.entity.Sex;
import com.nursecollab.domain.staff.entity.Staff;
import com.nursecollab.domain.workorder.entity.OrderPriority;
import com.nursecollab.domain.workorder.entity.WorkOrder;
import com.nursecollab.domain.workorder.entity.OrderStatus;

import java.time.OffsetDateTime;
import java.util.List;

/** 요청 상세(W-05 / E-02) */
public record WorkOrderDetailResponse(
        Long id,
        String requestNo,
        OrderStatus status,
        OrderPriority priority,
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
        List<OrderStatus> availableTransitions,
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
        var patient = encounter.getPatient();
        var serviceItem = request.getServiceItem();

        return new WorkOrderDetailResponse(
                request.getId(),
                request.getRequestNo(),
                request.getStatus(),
                request.getPriority(),
                new EncounterInfo(encounter.getId(), encounter.getRoomNo(),
                        encounter.getBedNo(), encounter.isMobile()),
                new PatientInfo(patient.getPatientNo(), patient.getName(),
                        patient.age(), patient.getSex()),
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
                List.copyOf(request.availableTransitions(viewer)),
                request.getVersion());
    }
}
