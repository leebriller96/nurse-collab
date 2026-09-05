package com.nursecollab.domain.transfer.dto;

import com.nursecollab.domain.department.dto.DepartmentSummary;
import com.nursecollab.domain.patient.dto.AlertResponse;
import com.nursecollab.domain.patient.dto.ChecklistWarning;
import com.nursecollab.domain.patient.entity.Sex;
import com.nursecollab.domain.staff.entity.Staff;
import com.nursecollab.domain.transfer.entity.TransferPriority;
import com.nursecollab.domain.transfer.entity.TransferRequest;
import com.nursecollab.domain.transfer.entity.TransferStatus;

import java.time.OffsetDateTime;
import java.util.List;

/** 요청 상세(W-05 / E-02) */
public record TransferDetailResponse(
        Long id,
        String requestNo,
        TransferStatus status,
        TransferPriority priority,
        EncounterInfo encounter,
        PatientInfo patient,
        ExamTypeInfo examType,
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
        List<TransferStatus> availableTransitions,
        Long version
) {
    public record EncounterInfo(Long encounterId, String roomNo, String bedNo, boolean isMobile) {}

    public record PatientInfo(String patientNo, String name, int age, Sex sex) {}

    public record ExamTypeInfo(Long id, String code, String name, int defaultDuration,
                               String prepInstruction) {}

    public record StaffInfo(Long id, String name) {}

    public static TransferDetailResponse of(TransferRequest request,
                                            Staff viewer,
                                            List<AlertResponse> alerts,
                                            List<ChecklistWarning> checklistWarnings) {
        var encounter = request.getEncounter();
        var patient = encounter.getPatient();
        var examType = request.getExamType();

        return new TransferDetailResponse(
                request.getId(),
                request.getRequestNo(),
                request.getStatus(),
                request.getPriority(),
                new EncounterInfo(encounter.getId(), encounter.getRoomNo(),
                        encounter.getBedNo(), encounter.isMobile()),
                new PatientInfo(patient.getPatientNo(), patient.getName(),
                        patient.age(), patient.getSex()),
                new ExamTypeInfo(examType.getId(), examType.getCode(), examType.getName(),
                        examType.getDefaultDuration(), examType.getPrepInstruction()),
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
