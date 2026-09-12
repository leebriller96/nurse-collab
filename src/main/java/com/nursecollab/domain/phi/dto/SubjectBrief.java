package com.nursecollab.domain.phi.dto;

import com.nursecollab.domain.encounter.entity.Encounter;
import com.nursecollab.domain.patient.entity.PatientAlert;
import com.nursecollab.domain.patient.entity.Sex;

import java.util.Collection;
import java.util.UUID;

/**
 * 목록 한 줄을 채우는 데 필요한 최소한.
 *
 * 상세({@link SubjectPhi})를 목록에도 그대로 쓰면 한 화면을 여는 것만으로
 * 스무 명의 진단명과 주의사항 전문이 밖으로 나간다. 큐에 필요한 것은
 * 이름과 "주의할 것이 몇 건 있는가" 뿐이다.
 *
 * @param criticalAlertCount 중대 주의사항 수. 큐에서 빨간 표시의 근거다.
 *                           무엇인지는 상세를 열어야 보인다.
 */
public record SubjectBrief(
        UUID subjectRef,
        String name,
        int age,
        Sex sex,
        int criticalAlertCount
) {
    public static SubjectBrief of(Encounter encounter, Collection<PatientAlert> alerts) {
        var patient = encounter.getPatient();
        return new SubjectBrief(
                encounter.getSubjectRef(),
                patient.getName(),
                patient.age(),
                patient.getSex(),
                (int) alerts.stream().filter(PatientAlert::isCritical).count());
    }
}
