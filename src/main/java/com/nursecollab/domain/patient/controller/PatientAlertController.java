package com.nursecollab.domain.patient.controller;

import com.nursecollab.domain.patient.dto.AlertResponse;
import com.nursecollab.domain.patient.dto.AlertCreateRequest;
import com.nursecollab.domain.patient.service.PatientAlertService;
import com.nursecollab.global.security.LoginStaff;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * 환자 주의사항.
 *
 * 경로가 재원이 아니라 환자인 것은 주의사항이 환자에 붙기 때문이다.
 * 퇴원한다고 인공관절이 사라지지 않는다. 다음 입원 때도 그대로 있어야 한다.
 */
@RestController
@RequestMapping("/api/v1/patients")
@RequiredArgsConstructor
public class PatientAlertController {

    private final PatientAlertService patientAlertService;

    @PostMapping("/{patientId}/alerts")
    public ResponseEntity<AlertResponse> add(
            @PathVariable Long patientId,
            @Valid @RequestBody AlertCreateRequest request,
            @AuthenticationPrincipal LoginStaff loginStaff) {

        AlertResponse created = patientAlertService.add(patientId, request, loginStaff);
        return ResponseEntity
                .created(URI.create("/api/v1/patients/" + patientId + "/alerts"))
                .body(created);
    }

    @PatchMapping("/alerts/{alertId}/deactivate")
    public ResponseEntity<Void> deactivate(
            @PathVariable Long alertId,
            @AuthenticationPrincipal LoginStaff loginStaff) {

        patientAlertService.deactivate(alertId, loginStaff);
        return ResponseEntity.noContent().build();
    }
}
