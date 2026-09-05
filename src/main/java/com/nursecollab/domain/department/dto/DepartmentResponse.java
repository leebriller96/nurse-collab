package com.nursecollab.domain.department.dto;

import com.nursecollab.domain.department.entity.Department;
import com.nursecollab.domain.department.entity.DeptType;

public record DepartmentResponse(
        Long id,
        String code,
        String name,
        DeptType deptType,
        String phone
) {
    public static DepartmentResponse from(Department department) {
        return new DepartmentResponse(
                department.getId(),
                department.getCode(),
                department.getName(),
                department.getDeptType(),
                department.getPhone());
    }
}
