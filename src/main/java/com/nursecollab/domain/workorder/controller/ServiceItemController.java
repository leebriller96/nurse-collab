package com.nursecollab.domain.workorder.controller;

import com.nursecollab.domain.workorder.dto.ServiceItemResponse;
import com.nursecollab.domain.workorder.service.ServiceItemQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/service-items")
@RequiredArgsConstructor
public class ServiceItemController {

    private final ServiceItemQueryService examTypeQueryService;

    @GetMapping
    public ResponseEntity<List<ServiceItemResponse>> findAll(
            @RequestParam(required = false) Long departmentId) {
        return ResponseEntity.ok(examTypeQueryService.findAll(departmentId));
    }
}
