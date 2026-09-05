package com.nursecollab.domain.master.dto;

import com.nursecollab.domain.patient.entity.AlertType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ExamTypeUpsertRequest(
        @NotBlank(message = "검사 코드는 필수입니다.")
        @Size(max = 20) String code,

        @NotBlank(message = "검사명은 필수입니다.")
        @Size(max = 100) String name,

        @NotNull(message = "담당 검사실은 필수입니다.") Long departmentId,

        @Min(value = 1, message = "소요시간을 확인해 주세요.")
        @Max(value = 480, message = "소요시간을 확인해 주세요.")
        int defaultDuration,

        String prepInstruction,

        /** 비우면 그 검사는 아무 경고도 띄우지 않는다. */
        List<AlertType> requiredAlerts
) {}
