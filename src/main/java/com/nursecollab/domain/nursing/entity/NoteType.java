package com.nursecollab.domain.nursing.entity;

public enum NoteType {

    GENERAL("일반 기록"),
    SBAR("SBAR"),
    HANDOVER("인수인계");

    private final String label;

    NoteType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
