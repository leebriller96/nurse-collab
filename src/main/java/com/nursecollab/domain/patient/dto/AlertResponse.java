package com.nursecollab.domain.patient.dto;

import com.nursecollab.domain.patient.entity.AlertSeverity;
import com.nursecollab.domain.patient.entity.AlertType;
import com.nursecollab.domain.patient.entity.PatientAlert;

import java.time.OffsetDateTime;

public record AlertResponse(
        Long id,
        AlertType alertType,
        String label,
        AlertSeverity severity,
        String content,
        OffsetDateTime createdAt
) {
    public static AlertResponse from(PatientAlert alert) {
        return new AlertResponse(
                alert.getId(),
                alert.getAlertType(),
                alert.getAlertType().getLabel(),
                alert.getSeverity(),
                alert.getContent(),
                alert.getCreatedAt());
    }
}
