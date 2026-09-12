package com.nursecollab.domain.workorder.service;

import com.nursecollab.domain.workorder.dto.ServiceItemResponse;
import com.nursecollab.domain.workorder.entity.ServiceItem;
import com.nursecollab.domain.workorder.repository.ServiceItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
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

        // 종류 순서는 OrderType 이 선언된 순서를 따른다.
        // DB 는 코드 알파벳순으로 주는데, 그러면 BME_* 가 맨 앞에 와서
        // 요청 화면의 첫 탭이 의공이 된다. 병동 간호사는 매번 탭을 옮겨야 한다.
        // 어느 업무가 주된 것인지는 도메인이 아는 것이지 코드 철자가 아니다.
        return serviceItems.stream()
                .sorted(Comparator.comparing(ServiceItem::getOrderType)
                        .thenComparing(ServiceItem::getCode))
                .map(ServiceItemResponse::from)
                .toList();
    }
}
