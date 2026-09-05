package com.nursecollab.domain.master.dto;

import com.nursecollab.domain.department.dto.DepartmentSummary;
import com.nursecollab.domain.staff.entity.Staff;
import com.nursecollab.domain.staff.entity.StaffRole;

import java.time.OffsetDateTime;

public record StaffAdminResponse(
        Long id,
        String loginId,
        String employeeNo,
        String name,
        StaffRole role,
        DepartmentSummary department,
        String phone,
        boolean active,
        OffsetDateTime lastLoginAt
) {
    public static StaffAdminResponse from(Staff staff) {
        return new StaffAdminResponse(
                staff.getId(), staff.getLoginId(), staff.getEmployeeNo(), staff.getName(),
                staff.getRole(), DepartmentSummary.from(staff.getDepartment()),
                staff.getPhone(), staff.isActive(), staff.getLastLoginAt());
    }
}
