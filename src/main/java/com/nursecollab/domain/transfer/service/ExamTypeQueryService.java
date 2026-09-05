package com.nursecollab.domain.transfer.service;

import com.nursecollab.domain.transfer.dto.ExamTypeResponse;
import com.nursecollab.domain.transfer.repository.ExamTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExamTypeQueryService {

    private final ExamTypeRepository examTypeRepository;

    public List<ExamTypeResponse> findAll(Long departmentId) {
        var examTypes = (departmentId == null)
                ? examTypeRepository.findAllActiveWithDepartment()
                : examTypeRepository.findAllActiveByDepartmentWithDepartment(departmentId);

        return examTypes.stream()
                .map(ExamTypeResponse::from)
                .toList();
    }
}
