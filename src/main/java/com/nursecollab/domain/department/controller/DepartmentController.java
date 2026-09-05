package com.nursecollab.domain.department.controller;

import com.nursecollab.domain.department.dto.DepartmentResponse;
import com.nursecollab.domain.department.entity.DeptType;
import com.nursecollab.domain.department.service.DepartmentQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/departments")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentQueryService departmentQueryService;

    @GetMapping
    public ResponseEntity<List<DepartmentResponse>> findAll(
            @RequestParam(required = false) DeptType deptType) {
        return ResponseEntity.ok(departmentQueryService.findAll(deptType));
    }
}
