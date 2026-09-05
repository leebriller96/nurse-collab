package com.nursecollab.domain.patient.dto;

import com.nursecollab.domain.patient.entity.AlertType;
import com.nursecollab.domain.patient.entity.PatientAlert;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 검사의 필수 확인 항목과 환자의 주의사항이 겹치는 지점.
 * 검사실 화면에 "MRI 금기 가능성" 같은 경고로 뜬다. 이 시스템의 핵심 가치다.
 */
public record ChecklistWarning(AlertType alertType, String message) {

    public static List<ChecklistWarning> cross(Collection<AlertType> requiredAlerts,
                                               Collection<PatientAlert> patientAlerts) {
        if (requiredAlerts.isEmpty() || patientAlerts.isEmpty()) {
            return List.of();
        }
        Set<AlertType> present = EnumSet.noneOf(AlertType.class);
        patientAlerts.forEach(a -> present.add(a.getAlertType()));

        return requiredAlerts.stream()
                .distinct()
                .filter(present::contains)
                .map(type -> new ChecklistWarning(type, type.getWarningMessage()))
                .toList();
    }
}
