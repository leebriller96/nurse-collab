package com.nursecollab.domain.workorder.service;

import com.nursecollab.domain.workorder.dto.ServiceItemResponse;
import com.nursecollab.domain.workorder.repository.ServiceItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ServiceItemQueryService {

    private final ServiceItemRepository serviceItemRepository;

    public List<ServiceItemResponse> findAll(Long departmentId) {
        var serviceItems = (departmentId == null)
                ? serviceItemRepository.findAllActiveWithDepartment()
                : serviceItemRepository.findAllActiveByDepartmentWithDepartment(departmentId);

        return serviceItems.stream()
                .map(ServiceItemResponse::from)
                .toList();
    }
}
