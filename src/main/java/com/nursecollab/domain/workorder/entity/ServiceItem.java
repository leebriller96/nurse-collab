package com.nursecollab.domain.workorder.entity;

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
import java.util.stream.Collectors;
import java.util.List;

/**
 * 업무 항목 마스터. 부서가 무엇을 해 주는지를 적는다 (뇌 MRI, 혈액 채취, 주사제 조제 ...).
 * 이 테이블에는 생성/수정 시각 컬럼이 없어서 BaseTimeEntity 를 상속하지 않는다.
 */
@Entity
@Table(name = "service_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ServiceItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    /** 이 업무를 수행하는 파트. 업무 요청의 수행 파트가 여기서 결정된다. */
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

    public static ServiceItem create(String code, String name, Department department,
                                  int defaultDuration, String prepInstruction,
                                  List<AlertType> requiredAlerts) {
        ServiceItem serviceItem = new ServiceItem();
        serviceItem.code = code;
        serviceItem.name = name;
        serviceItem.department = department;
        serviceItem.defaultDuration = defaultDuration;
        serviceItem.prepInstruction = prepInstruction;
        serviceItem.requiredAlerts = joinAlerts(requiredAlerts);
        serviceItem.active = true;
        return serviceItem;
    }

    public void update(String code, String name, Department department, int defaultDuration,
                       String prepInstruction, List<AlertType> requiredAlerts) {
        this.code = code;
        this.name = name;
        this.department = department;
        this.defaultDuration = defaultDuration;
        this.prepInstruction = prepInstruction;
        this.requiredAlerts = joinAlerts(requiredAlerts);
    }

    /** 지우지 않는다. 지난 요청이 이 업무 항목을 참조하고 있다. */
    public void deactivate() {
        this.active = false;
    }

    private static String joinAlerts(List<AlertType> alerts) {
        return (alerts == null || alerts.isEmpty())
                ? null
                : alerts.stream().map(Enum::name).collect(Collectors.joining(","));
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
