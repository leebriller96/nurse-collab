package com.nursecollab.domain.encounter.entity;

public enum EncounterStatus {

    ADMITTED("재원"),
    DISCHARGED("퇴원");

    private final String label;

    EncounterStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
