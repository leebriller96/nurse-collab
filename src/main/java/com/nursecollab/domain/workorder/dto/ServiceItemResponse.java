package com.nursecollab.domain.workorder.dto;

import com.nursecollab.domain.patient.entity.AlertType;
import com.nursecollab.domain.workorder.entity.OrderType;
import com.nursecollab.domain.workorder.entity.ServiceItem;

import java.util.List;

public record ServiceItemResponse(
        Long id,
        String code,
        String name,
        OrderType orderType,
        /** 화면이 "이송 / 검체" 처럼 사람 말로 보여줄 수 있게 함께 내려보낸다 */
        String orderTypeLabel,
        /** 이 업무에 대상 환자가 필요한가. 요청 화면이 환자 선택칸을 띄울지 정한다 */
        boolean patientRequired,
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
                serviceItem.getOrderType(),
                serviceItem.getOrderType().getLabel(),
                serviceItem.getOrderType().isPatientRequired(),
                new DepartmentRef(serviceItem.getDepartment().getId(),
                        serviceItem.getDepartment().getName()),
                serviceItem.getDefaultDuration(),
                serviceItem.getPrepInstruction(),
                serviceItem.requiredAlertTypes());
    }
}
