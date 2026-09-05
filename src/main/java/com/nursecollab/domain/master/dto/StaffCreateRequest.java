package com.nursecollab.domain.master.dto;

import com.nursecollab.domain.staff.entity.StaffRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StaffCreateRequest(
        @NotBlank(message = "아이디는 필수입니다.")
        @Size(max = 50) String loginId,

        @NotBlank(message = "초기 비밀번호는 필수입니다.")
        @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.")
        String password,

        @NotBlank(message = "사번은 필수입니다.")
        @Size(max = 30) String employeeNo,

        @NotBlank(message = "이름은 필수입니다.")
        @Size(max = 50) String name,

        @NotNull(message = "역할은 필수입니다.") StaffRole role,
        @NotNull(message = "소속 부서는 필수입니다.") Long departmentId,
        @Size(max = 30) String phone
) {}
