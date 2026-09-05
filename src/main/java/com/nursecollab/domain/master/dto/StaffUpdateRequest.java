package com.nursecollab.domain.master.dto;

import com.nursecollab.domain.staff.entity.StaffRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 비밀번호는 여기서 다루지 않는다. 관리자가 남의 비밀번호를 바꿀 수 있으면 안 된다. */
public record StaffUpdateRequest(
        @NotBlank(message = "이름은 필수입니다.")
        @Size(max = 50) String name,

        @NotNull(message = "역할은 필수입니다.") StaffRole role,
        @NotNull(message = "소속 부서는 필수입니다.") Long departmentId,
        @Size(max = 30) String phone
) {}
