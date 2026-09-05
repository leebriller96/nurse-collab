package com.nursecollab.domain.patient.entity;

public enum AlertSeverity {

    INFO("참고"),
    WARN("주의"),
    CRITICAL("중대");

    private final String label;

    AlertSeverity(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
