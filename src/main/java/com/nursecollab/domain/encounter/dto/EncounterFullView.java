package com.nursecollab.domain.encounter.dto;

import com.nursecollab.domain.department.dto.DepartmentSummary;
import com.nursecollab.domain.encounter.entity.Encounter;
import com.nursecollab.domain.patient.dto.AlertResponse;
import com.nursecollab.domain.patient.entity.Sex;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** 담당 병동(과 관리자)이 보는 전체 정보 */
public record EncounterFullView(
        Long encounterId,
        PatientInfo patient,
        DepartmentSummary department,
        String roomNo,
        String bedNo,
        OffsetDateTime admittedAt,
        String diagnosis,
        boolean isMobile,
        List<AlertResponse> alerts,
        List<ActiveRequest> activeRequests
) implements EncounterView {

    /** 주의사항은 환자에 붙으므로 붙이려면 환자 식별자가 필요하다 */
    public record PatientInfo(Long id, String patientNo, String name, LocalDate birthDate, int age, Sex sex) {}

    public record ActiveRequest(Long id, String requestNo, String itemName, String status,
                                OffsetDateTime scheduledAt) {}

    public static EncounterFullView of(Encounter encounter,
                                       List<AlertResponse> alerts,
                                       List<ActiveRequest> activeRequests) {
        var patient = encounter.getPatient();
        return new EncounterFullView(
                encounter.getId(),
                new PatientInfo(patient.getId(), patient.getPatientNo(), patient.getName(),
                        patient.getBirthDate(), patient.age(), patient.getSex()),
                DepartmentSummary.from(encounter.getDepartment()),
                encounter.getRoomNo(),
                encounter.getBedNo(),
                encounter.getAdmittedAt(),
                encounter.getDiagnosis(),
                encounter.isMobile(),
                alerts,
                activeRequests);
    }
}
