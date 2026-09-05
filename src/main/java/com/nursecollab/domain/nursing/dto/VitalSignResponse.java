package com.nursecollab.domain.nursing.dto;

import com.nursecollab.domain.nursing.entity.VitalSign;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record VitalSignResponse(
        Long id,
        OffsetDateTime measuredAt,
        BigDecimal temperature,
        Integer pulse,
        Integer respiration,
        Integer sbp,
        Integer dbp,
        Integer spo2,
        Integer painScore,
        RecorderInfo recordedBy
) {
    public record RecorderInfo(Long id, String name) {}

    public static VitalSignResponse from(VitalSign vital) {
        return new VitalSignResponse(
                vital.getId(),
                vital.getMeasuredAt(),
                vital.getTemperature(),
                vital.getPulse(),
                vital.getRespiration(),
                vital.getSbp(),
                vital.getDbp(),
                vital.getSpo2(),
                vital.getPainScore(),
                new RecorderInfo(vital.getRecordedBy().getId(), vital.getRecordedBy().getName()));
    }
}
