package com.nursecollab.domain.master.dto;

import com.nursecollab.domain.patient.entity.AlertType;
import com.nursecollab.domain.workorder.entity.OrderType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ServiceItemUpsertRequest(
        @NotBlank(message = "업무 코드는 필수입니다.")
        @Size(max = 20) String code,

        @NotBlank(message = "업무명은 필수입니다.")
        @Size(max = 100) String name,

        /**
         * 어떤 흐름을 탈지 정한다. 만들 때만 받고 수정에서는 무시한다.
         * 이미 이 항목으로 진행 중인 요청들이 각자의 흐름 위에 있기 때문이다.
         */
        @NotNull(message = "업무 종류는 필수입니다.") OrderType orderType,

        @NotNull(message = "담당 파트는 필수입니다.") Long departmentId,

        @Min(value = 1, message = "소요시간을 확인해 주세요.")
        @Max(value = 480, message = "소요시간을 확인해 주세요.")
        int defaultDuration,

        String prepInstruction,

        /** 비우면 그 업무는 아무 경고도 띄우지 않는다. */
        List<AlertType> requiredAlerts
) {}
