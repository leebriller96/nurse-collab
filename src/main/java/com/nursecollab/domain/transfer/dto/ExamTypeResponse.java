package com.nursecollab.domain.transfer.dto;

import com.nursecollab.domain.transfer.entity.ExamType;

import java.util.List;

public record ExamTypeResponse(
        Long id,
        String code,
        String name,
        DepartmentRef department,
        int defaultDuration,
        String prepInstruction,
        List<String> requiredAlerts
) {
    public record DepartmentRef(Long id, String name) {}

    public static ExamTypeResponse from(ExamType examType) {
        return new ExamTypeResponse(
                examType.getId(),
                examType.getCode(),
                examType.getName(),
                new DepartmentRef(examType.getDepartment().getId(),
                        examType.getDepartment().getName()),
                examType.getDefaultDuration(),
                examType.getPrepInstruction(),
                examType.requiredAlertTypes());
    }
}
