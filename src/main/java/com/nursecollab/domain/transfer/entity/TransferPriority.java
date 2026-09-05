package com.nursecollab.domain.transfer.entity;

public enum TransferPriority {

    ROUTINE("일반"),
    URGENT("긴급"),
    EMERGENCY("응급");

    private final String label;

    TransferPriority(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
