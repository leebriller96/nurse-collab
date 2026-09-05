package com.nursecollab.domain.transfer.controller;

import com.nursecollab.domain.transfer.dto.ExamTypeResponse;
import com.nursecollab.domain.transfer.service.ExamTypeQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/exam-types")
@RequiredArgsConstructor
public class ExamTypeController {

    private final ExamTypeQueryService examTypeQueryService;

    @GetMapping
    public ResponseEntity<List<ExamTypeResponse>> findAll(
            @RequestParam(required = false) Long departmentId) {
        return ResponseEntity.ok(examTypeQueryService.findAll(departmentId));
    }
}
