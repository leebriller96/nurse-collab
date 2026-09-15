package com.nursecollab.domain.phi.controller;

import com.nursecollab.domain.phi.dto.PhiAccessLogResponse;
import com.nursecollab.domain.phi.service.PhiAccessLogQueryService;
import com.nursecollab.global.common.PageResponse;
import com.nursecollab.global.security.LoginStaff;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** A-05 환자 정보 열람 기록. 원내 경로로만 열린다. */
@RestController
@RequestMapping("/api/v1/phi/access-logs")
@RequiredArgsConstructor
public class PhiAccessLogController {

    private final PhiAccessLogQueryService queryService;

    @GetMapping
    public ResponseEntity<PageResponse<PhiAccessLogResponse>> search(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String patientNo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size,
            @AuthenticationPrincipal LoginStaff loginStaff) {

        return ResponseEntity.ok(queryService.search(
                from, to, patientNo, PageRequest.of(page, size), loginStaff));
    }
}
