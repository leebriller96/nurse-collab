package com.nursecollab.domain.master.dto;

import com.nursecollab.domain.department.entity.DeptType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DepartmentUpsertRequest(
        @NotBlank(message = "부서 코드는 필수입니다.")
        @Size(max = 20, message = "부서 코드는 20자를 넘을 수 없습니다.")
        String code,

        @NotBlank(message = "부서명은 필수입니다.")
        @Size(max = 100, message = "부서명은 100자를 넘을 수 없습니다.")
        String name,

        @NotNull(message = "부서 유형은 필수입니다.")
        DeptType deptType,

        @Size(max = 100) String location,
        @Size(max = 30) String phone
) {}
