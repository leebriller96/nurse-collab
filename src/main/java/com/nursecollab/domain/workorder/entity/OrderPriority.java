package com.nursecollab.domain.workorder.entity;

public enum OrderPriority {

    ROUTINE("일반"),
    URGENT("긴급"),
    EMERGENCY("응급");

    private final String label;

    OrderPriority(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
