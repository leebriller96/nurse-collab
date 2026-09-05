package com.nursecollab.domain.transfer.dto;

import com.nursecollab.domain.department.dto.DepartmentSummary;
import com.nursecollab.domain.patient.entity.Sex;
import com.nursecollab.domain.transfer.entity.TransferPriority;
import com.nursecollab.domain.transfer.entity.TransferRequest;
import com.nursecollab.domain.transfer.entity.TransferStatus;

import java.time.Duration;
import java.time.OffsetDateTime;

/** 큐(E-01)와 현황(W-04)의 행 하나 */
public record TransferSummary(
        Long id,
        String requestNo,
        TransferStatus status,
        TransferPriority priority,
        PatientInfo patient,
        String roomNo,
        String examName,
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
    public static TransferSummary of(TransferRequest request, boolean inbound, int criticalAlertCount) {
        var encounter = request.getEncounter();
        var patient = encounter.getPatient();
        var counterpart = inbound ? request.getFromDepartment() : request.getToDepartment();

        return new TransferSummary(
                request.getId(),
                request.getRequestNo(),
                request.getStatus(),
                request.getPriority(),
                new PatientInfo(patient.getPatientNo(), patient.getName(),
                        patient.age(), patient.getSex()),
                encounter.getRoomNo(),
                request.getExamType().getName(),
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
    private static long waitingMinutes(TransferRequest request) {
        OffsetDateTime end = request.getStatus().isTerminal() && request.getCompletedAt() != null
                ? request.getCompletedAt()
                : OffsetDateTime.now();
        return Math.max(0, Duration.between(request.getRequestedAt(), end).toMinutes());
    }
}
