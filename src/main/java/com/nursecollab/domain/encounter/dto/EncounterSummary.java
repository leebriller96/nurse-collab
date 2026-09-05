package com.nursecollab.domain.encounter.dto;

import com.nursecollab.domain.encounter.entity.Encounter;
import com.nursecollab.domain.patient.dto.AlertSummary;
import com.nursecollab.domain.patient.entity.Sex;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** 환자 보드 카드 하나 */
public record EncounterSummary(
        Long encounterId,
        String patientNo,
        String name,
        LocalDate birthDate,
        int age,
        Sex sex,
        String roomNo,
        String bedNo,
        OffsetDateTime admittedAt,
        String diagnosis,
        List<AlertSummary> alertSummary,
        int activeRequestCount
) {
    public static EncounterSummary of(Encounter encounter,
                                      List<AlertSummary> alerts,
                                      int activeRequestCount) {
        var patient = encounter.getPatient();
        return new EncounterSummary(
                encounter.getId(),
                patient.getPatientNo(),
                patient.getName(),
                patient.getBirthDate(),
                patient.age(),
                patient.getSex(),
                encounter.getRoomNo(),
                encounter.getBedNo(),
                encounter.getAdmittedAt(),
                encounter.getDiagnosis(),
                alerts,
                activeRequestCount);
    }
}
