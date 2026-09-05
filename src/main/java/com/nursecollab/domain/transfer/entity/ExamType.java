package com.nursecollab.domain.transfer.entity;

import com.nursecollab.domain.department.entity.Department;
import com.nursecollab.domain.patient.entity.AlertType;
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

import java.util.Arrays;
import java.util.List;

/**
 * 검사 종류 마스터.
 * 이 테이블에는 생성/수정 시각 컬럼이 없어서 BaseTimeEntity 를 상속하지 않는다.
 */
@Entity
@Table(name = "exam_type")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExamType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    /** 이 검사를 수행하는 파트. 이송 요청의 수행 파트가 여기서 결정된다. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id")
    private Department department;

    @Column(name = "default_duration", nullable = false)
    private int defaultDuration;

    @Column(name = "prep_instruction", columnDefinition = "text")
    private String prepInstruction;

    @Column(name = "required_alerts", length = 200)
    private String requiredAlerts;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    public static ExamType create(String code, String name, Department department,
                                  int defaultDuration, String prepInstruction,
                                  List<AlertType> requiredAlerts) {
        ExamType examType = new ExamType();
        examType.code = code;
        examType.name = name;
        examType.department = department;
        examType.defaultDuration = defaultDuration;
        examType.prepInstruction = prepInstruction;
        examType.requiredAlerts = (requiredAlerts == null || requiredAlerts.isEmpty())
                ? null : requiredAlerts.stream().map(Enum::name).collect(java.util.stream.Collectors.joining(","));
        examType.active = true;
        return examType;
    }

    /** 이 검사를 하기 전에 반드시 확인해야 하는 주의사항 유형 */
    public List<AlertType> requiredAlertTypes() {
        if (requiredAlerts == null || requiredAlerts.isBlank()) {
            return List.of();
        }
        return Arrays.stream(requiredAlerts.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(AlertType::valueOf)
                .toList();
    }
}
