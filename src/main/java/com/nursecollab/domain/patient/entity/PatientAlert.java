package com.nursecollab.domain.patient.entity;

import com.nursecollab.domain.staff.entity.Staff;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

import java.time.OffsetDateTime;

/**
 * 환자 주의사항.
 * 이 테이블에는 수정 시각 컬럼이 없어서 BaseTimeEntity 를 상속하지 않는다.
 * 내용을 고치는 대신 비활성화하고 새로 다는 것을 전제로 한 설계다.
 */
@Entity
@Table(name = "patient_alert")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PatientAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id")
    private Patient patient;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false, length = 30)
    private AlertType alertType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AlertSeverity severity = AlertSeverity.INFO;

    @Column(nullable = false, length = 500)
    private String content;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by")
    private Staff createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public static PatientAlert create(Patient patient, AlertType alertType,
                                      AlertSeverity severity, String content, Staff createdBy) {
        PatientAlert alert = new PatientAlert();
        alert.patient = patient;
        alert.alertType = alertType;
        alert.severity = severity;
        alert.content = content;
        alert.createdBy = createdBy;
        alert.active = true;
        alert.createdAt = OffsetDateTime.now();
        return alert;
    }

    /** 기록은 지우지 않고 비활성화만 한다 */
    public void deactivate() {
        this.active = false;
    }

    public boolean isCritical() {
        return severity == AlertSeverity.CRITICAL;
    }
}
