package com.nursecollab.domain.nursing.entity;

import com.nursecollab.domain.encounter.entity.Encounter;
import com.nursecollab.domain.staff.entity.Staff;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 활력징후.
 * 측정한 값은 고치지 않는다. 잘못 적었으면 다시 측정해 새로 남긴다.
 */
@Entity
@Table(name = "vital_sign")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VitalSign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "encounter_id")
    private Encounter encounter;

    @Column(name = "measured_at", nullable = false)
    private OffsetDateTime measuredAt;

    @Column(precision = 4, scale = 1)
    private BigDecimal temperature;

    private Integer pulse;
    private Integer respiration;
    private Integer sbp;
    private Integer dbp;
    private Integer spo2;

    @Column(name = "pain_score")
    private Integer painScore;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recorded_by")
    private Staff recordedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public static VitalSign record(Encounter encounter, OffsetDateTime measuredAt,
                                   BigDecimal temperature, Integer pulse, Integer respiration,
                                   Integer sbp, Integer dbp, Integer spo2, Integer painScore,
                                   Staff recordedBy) {
        VitalSign vital = new VitalSign();
        vital.encounter = encounter;
        vital.measuredAt = measuredAt;
        vital.temperature = temperature;
        vital.pulse = pulse;
        vital.respiration = respiration;
        vital.sbp = sbp;
        vital.dbp = dbp;
        vital.spo2 = spo2;
        vital.painScore = painScore;
        vital.recordedBy = recordedBy;
        vital.createdAt = OffsetDateTime.now();
        return vital;
    }
}
