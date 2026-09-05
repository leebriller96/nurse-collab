package com.nursecollab.domain.department.service;

import com.nursecollab.domain.department.dto.DepartmentResponse;
import com.nursecollab.domain.department.entity.DeptType;
import com.nursecollab.domain.department.repository.DepartmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DepartmentQueryService {

    private final DepartmentRepository departmentRepository;

    public List<DepartmentResponse> findAll(DeptType deptType) {
        var departments = (deptType == null)
                ? departmentRepository.findAllByActiveTrueOrderByCodeAsc()
                : departmentRepository.findAllByDeptTypeAndActiveTrueOrderByCodeAsc(deptType);

        return departments.stream()
                .map(DepartmentResponse::from)
                .toList();
    }
}
