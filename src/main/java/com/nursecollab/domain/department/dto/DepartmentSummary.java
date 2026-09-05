package com.nursecollab.domain.department.dto;

import com.nursecollab.domain.department.entity.Department;
import com.nursecollab.domain.department.entity.DeptType;

/** 다른 응답에 끼워 넣는 파트 요약. 프론트는 deptType 으로 화면을 분기한다. */
public record DepartmentSummary(
        Long id,
        String code,
        String name,
        DeptType deptType
) {
    public static DepartmentSummary from(Department department) {
        return new DepartmentSummary(
                department.getId(),
                department.getCode(),
                department.getName(),
                department.getDeptType());
    }
}
