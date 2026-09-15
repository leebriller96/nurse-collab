package com.nursecollab.domain.phi.dto;

import com.nursecollab.domain.encounter.entity.Encounter;
import com.nursecollab.domain.patient.dto.AlertSummary;
import com.nursecollab.domain.patient.entity.PatientAlert;
import com.nursecollab.domain.patient.entity.Sex;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * 목록 한 줄을 채우는 데 필요한 최소한.
 *
 * 상세({@link SubjectPhi})를 목록에도 그대로 쓰면 한 화면을 여는 것만으로
 * 스무 명의 주의사항 전문이 밖으로 나간다. 목록에 필요한 것은 그만큼이 아니다.
 *
 * <p><b>보는 사람에 따라 담기는 것이 달라진다.</b>
 * 담당 병동은 진단명과 주의사항 뱃지까지 보고, 검사실은 이름과 "주의할 것이 몇 건"
 * 까지만 본다. 검사실 상세 화면에 진단명이 실리지 않는 것과 같은 규칙이다 —
 * 검사를 수행하는 데 진단명은 필요하지 않다.
 *
 * @param criticalAlertCount 중대 주의사항 수. 큐에서 빨간 표시의 근거다.
 *                           무엇인지는 상세를 열어야 보인다.
 * @param diagnosis          담당 병동이 아니면 비어 있다
 * @param alerts             유형과 심각도만. 내용은 담지 않는다. 담당 병동이 아니면 비어 있다
 */
public record SubjectBrief(
        UUID subjectRef,
        String name,
        int age,
        Sex sex,
        int criticalAlertCount,
        String diagnosis,
        List<AlertSummary> alerts
) {
    public static SubjectBrief of(Encounter encounter, Collection<PatientAlert> alerts,
                                  boolean ownWard) {
        var patient = encounter.getPatient();
        return new SubjectBrief(
                encounter.getSubjectRef(),
                patient.getName(),
                patient.age(),
                patient.getSex(),
                (int) alerts.stream().filter(PatientAlert::isCritical).count(),
                ownWard ? encounter.getDiagnosis() : null,
                ownWard ? alerts.stream().map(AlertSummary::from).toList() : List.of());
    }
}
