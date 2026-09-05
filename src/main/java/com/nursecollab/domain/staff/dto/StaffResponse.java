package com.nursecollab.domain.staff.dto;

import com.nursecollab.domain.department.dto.DepartmentSummary;
import com.nursecollab.domain.staff.entity.Staff;
import com.nursecollab.domain.staff.entity.StaffRole;

public record StaffResponse(
        Long id,
        String name,
        StaffRole role,
        DepartmentSummary department
) {
    public static StaffResponse from(Staff staff) {
        return new StaffResponse(
                staff.getId(),
                staff.getName(),
                staff.getRole(),
                DepartmentSummary.from(staff.getDepartment()));
    }
}
