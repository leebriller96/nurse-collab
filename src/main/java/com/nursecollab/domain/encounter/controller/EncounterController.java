package com.nursecollab.domain.encounter.controller;

import com.nursecollab.domain.encounter.dto.EncounterSummary;
import com.nursecollab.domain.encounter.dto.EncounterView;
import com.nursecollab.domain.encounter.entity.EncounterStatus;
import com.nursecollab.domain.encounter.service.EncounterQueryService;
import com.nursecollab.domain.patient.dto.AlertResponse;
import com.nursecollab.global.audit.Audited;
import com.nursecollab.global.common.PageResponse;
import com.nursecollab.global.security.LoginStaff;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/encounters")
@RequiredArgsConstructor
public class EncounterController {

    private final EncounterQueryService encounterQueryService;

    @GetMapping
    public ResponseEntity<PageResponse<EncounterSummary>> search(
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) EncounterStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal LoginStaff loginStaff) {

        return ResponseEntity.ok(encounterQueryService.findAll(
                departmentId, status, keyword, PageRequest.of(page, size), loginStaff));
    }

    /** 호출자의 파트에 따라 응답 모양이 달라진다. 검사실에는 진단명이 아예 실리지 않는다. */
    @GetMapping("/{encounterId}")
    @Audited(action = "VIEW", targetType = "ENCOUNTER", targetIdParam = "encounterId")
    public ResponseEntity<EncounterView> detail(
            @PathVariable Long encounterId,
            @AuthenticationPrincipal LoginStaff loginStaff) {
        return ResponseEntity.ok(encounterQueryService.findDetail(encounterId, loginStaff));
    }

    @GetMapping("/{encounterId}/alerts")
    @Audited(action = "VIEW", targetType = "ENCOUNTER", targetIdParam = "encounterId")
    public ResponseEntity<List<AlertResponse>> alerts(
            @PathVariable Long encounterId,
            @AuthenticationPrincipal LoginStaff loginStaff) {
        return ResponseEntity.ok(encounterQueryService.findAlerts(encounterId, loginStaff));
    }
}
