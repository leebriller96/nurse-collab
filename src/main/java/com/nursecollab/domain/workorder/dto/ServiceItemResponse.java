package com.nursecollab.domain.workorder.dto;

import com.nursecollab.domain.patient.entity.AlertType;
import com.nursecollab.domain.workorder.entity.ServiceItem;

import java.util.List;

public record ServiceItemResponse(
        Long id,
        String code,
        String name,
        DepartmentRef department,
        int defaultDuration,
        String prepInstruction,
        List<AlertType> requiredAlerts
) {
    public record DepartmentRef(Long id, String name) {}

    public static ServiceItemResponse from(ServiceItem serviceItem) {
        return new ServiceItemResponse(
                serviceItem.getId(),
                serviceItem.getCode(),
                serviceItem.getName(),
                new DepartmentRef(serviceItem.getDepartment().getId(),
                        serviceItem.getDepartment().getName()),
                serviceItem.getDefaultDuration(),
                serviceItem.getPrepInstruction(),
                serviceItem.requiredAlertTypes());
    }
}
