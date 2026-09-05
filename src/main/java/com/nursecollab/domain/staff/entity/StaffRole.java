package com.nursecollab.domain.staff.entity;

public enum StaffRole {

    NURSE("간호사"),
    HEAD_NURSE("수간호사"),
    ADMIN("관리자");

    private final String label;

    StaffRole(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** 통계처럼 파트 전체를 볼 수 있는 역할인가 */
    public boolean canViewDepartmentStats() {
        return this == HEAD_NURSE || this == ADMIN;
    }
}
