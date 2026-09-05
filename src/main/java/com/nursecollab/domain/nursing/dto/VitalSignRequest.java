package com.nursecollab.domain.nursing.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** 범위를 벗어난 값은 오타일 가능성이 높다. 저장 전에 걸러 준다. */
public record VitalSignRequest(
        @NotNull(message = "측정 시각은 필수입니다.")
        OffsetDateTime measuredAt,

        @DecimalMin(value = "30.0", message = "체온을 다시 확인해 주세요.")
        @DecimalMax(value = "45.0", message = "체온을 다시 확인해 주세요.")
        BigDecimal temperature,

        @Min(value = 20, message = "맥박을 다시 확인해 주세요.")
        @Max(value = 250, message = "맥박을 다시 확인해 주세요.")
        Integer pulse,

        @Min(value = 4, message = "호흡수를 다시 확인해 주세요.")
        @Max(value = 60, message = "호흡수를 다시 확인해 주세요.")
        Integer respiration,

        @Min(value = 40, message = "수축기 혈압을 다시 확인해 주세요.")
        @Max(value = 260, message = "수축기 혈압을 다시 확인해 주세요.")
        Integer sbp,

        @Min(value = 20, message = "이완기 혈압을 다시 확인해 주세요.")
        @Max(value = 200, message = "이완기 혈압을 다시 확인해 주세요.")
        Integer dbp,

        @Min(value = 50, message = "산소포화도를 다시 확인해 주세요.")
        @Max(value = 100, message = "산소포화도를 다시 확인해 주세요.")
        Integer spo2,

        @Min(value = 0, message = "통증 점수는 0에서 10 사이입니다.")
        @Max(value = 10, message = "통증 점수는 0에서 10 사이입니다.")
        Integer painScore
) {}
