package com.nursecollab.domain.notification.entity;

public enum NotiType {

    TRANSFER_REQUESTED("새 요청"),
    STATUS_CHANGED("상태 변경"),
    MESSAGE("새 메시지");

    private final String label;

    NotiType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
