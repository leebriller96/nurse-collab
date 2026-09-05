package com.nursecollab.domain.patient.dto;

import com.nursecollab.domain.patient.entity.AlertSeverity;
import com.nursecollab.domain.patient.entity.AlertType;
import com.nursecollab.domain.patient.entity.PatientAlert;

/** 목록 화면의 뱃지용. 내용은 빼고 유형과 심각도만 준다. */
public record AlertSummary(AlertType alertType, AlertSeverity severity) {

    public static AlertSummary from(PatientAlert alert) {
        return new AlertSummary(alert.getAlertType(), alert.getSeverity());
    }
}
