package com.nursecollab.domain.encounter.dto;

import com.nursecollab.domain.encounter.entity.Encounter;
import com.nursecollab.domain.patient.dto.AlertResponse;
import com.nursecollab.domain.patient.dto.ChecklistWarning;
import com.nursecollab.domain.patient.entity.Sex;

import java.util.List;

/**
 * 검사실이 보는 축약 정보.
 * 진단명, 생년월일, 활력징후는 필드 자체가 없다. 검사 수행에 필요하지 않기 때문이다.
 */
public record EncounterExamView(
        Long encounterId,
        PatientInfo patient,
        /** 이 환자가 있는 병동의 id. 이름은 화면이 부서 목록에서 찾아 채운다. */
        Long fromDepartmentId,
        String roomNo,
        boolean isMobile,
        List<AlertResponse> alerts,
        List<ChecklistWarning> checklistWarnings
) implements EncounterView {

    public record PatientInfo(String patientNo, String name, int age, Sex sex) {}

    public static EncounterExamView of(Encounter encounter,
                                       List<AlertResponse> alerts,
                                       List<ChecklistWarning> checklistWarnings) {
        var patient = encounter.getPatient();
        return new EncounterExamView(
                encounter.getId(),
                new PatientInfo(patient.getPatientNo(), patient.getName(),
                        patient.age(), patient.getSex()),
                encounter.getDepartmentId(),
                encounter.getRoomNo(),
                encounter.isMobile(),
                alerts,
                checklistWarnings);
    }
}
