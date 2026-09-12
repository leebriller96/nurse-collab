package com.nursecollab.domain.phi.dto;

import com.nursecollab.domain.encounter.entity.Encounter;
import com.nursecollab.domain.patient.dto.AlertResponse;
import com.nursecollab.domain.patient.entity.PatientAlert;
import com.nursecollab.domain.patient.entity.Sex;

import java.util.List;
import java.util.UUID;

/**
 * 가명 하나에 붙은 환자 정보.
 *
 * 업무 응답에는 이런 것이 하나도 없다. 화면이 이 조회로 따로 채운다.
 * 원내망 밖에서는 이 조회가 실패하고, 화면은 빈 자리를 감추는 대신
 * "원내망에서만 조회됩니다" 라고 말한다. 모르는 것보다 틀리게 아는 것이 나쁘다.
 *
 * @param diagnosis 진단명. 업무 쪽으로는 절대 나가지 않는다
 * @param mobile    자가 거동 가능 여부. 이송 준비물을 정하는 값이지만 건강 상태라 여기 있다
 */
public record SubjectPhi(
        UUID subjectRef,
        String patientNo,
        String name,
        int age,
        Sex sex,
        String diagnosis,
        boolean mobile,
        List<AlertResponse> alerts
) {
    public static SubjectPhi of(Encounter encounter, List<PatientAlert> alerts) {
        var patient = encounter.getPatient();
        return new SubjectPhi(
                encounter.getSubjectRef(),
                patient.getPatientNo(),
                patient.getName(),
                patient.age(),
                patient.getSex(),
                encounter.getDiagnosis(),
                encounter.isMobile(),
                alerts.stream().map(AlertResponse::from).toList());
    }
}
