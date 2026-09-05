package com.nursecollab.domain.master.controller;

import com.nursecollab.domain.department.dto.DepartmentResponse;
import com.nursecollab.domain.master.dto.DepartmentUpsertRequest;
import com.nursecollab.domain.master.dto.ExamTypeUpsertRequest;
import com.nursecollab.domain.master.dto.StaffAdminResponse;
import com.nursecollab.domain.master.dto.StaffCreateRequest;
import com.nursecollab.domain.master.dto.StaffUpdateRequest;
import com.nursecollab.domain.master.service.MasterAdminService;
import com.nursecollab.domain.staff.entity.StaffRole;
import com.nursecollab.domain.transfer.dto.ExamTypeResponse;
import com.nursecollab.global.error.BusinessException;
import com.nursecollab.global.error.ErrorCode;
import com.nursecollab.global.security.LoginStaff;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 마스터 관리. 조회는 각 도메인 컨트롤러가 하고 변경만 여기 모은다.
 * 변경은 전부 관리자만 한다.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class MasterAdminController {

    private final MasterAdminService masterAdminService;

    @PostMapping("/departments")
    public ResponseEntity<DepartmentResponse> createDepartment(
            @Valid @RequestBody DepartmentUpsertRequest request,
            @AuthenticationPrincipal LoginStaff loginStaff) {
        requireAdmin(loginStaff);
        return ResponseEntity.ok(masterAdminService.createDepartment(request));
    }

    @PutMapping("/departments/{id}")
    public ResponseEntity<DepartmentResponse> updateDepartment(
            @PathVariable Long id,
            @Valid @RequestBody DepartmentUpsertRequest request,
            @AuthenticationPrincipal LoginStaff loginStaff) {
        requireAdmin(loginStaff);
        return ResponseEntity.ok(masterAdminService.updateDepartment(id, request));
    }

    @PatchMapping("/departments/{id}/deactivate")
    public ResponseEntity<Void> deactivateDepartment(
            @PathVariable Long id, @AuthenticationPrincipal LoginStaff loginStaff) {
        requireAdmin(loginStaff);
        masterAdminService.deactivateDepartment(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/staff")
    public ResponseEntity<List<StaffAdminResponse>> findAllStaff(
            @AuthenticationPrincipal LoginStaff loginStaff) {
        requireAdmin(loginStaff);
        return ResponseEntity.ok(masterAdminService.findAllStaff());
    }

    @PostMapping("/staff")
    public ResponseEntity<StaffAdminResponse> createStaff(
            @Valid @RequestBody StaffCreateRequest request,
            @AuthenticationPrincipal LoginStaff loginStaff) {
        requireAdmin(loginStaff);
        return ResponseEntity.ok(masterAdminService.createStaff(request));
    }

    @PutMapping("/staff/{id}")
    public ResponseEntity<StaffAdminResponse> updateStaff(
            @PathVariable Long id,
            @Valid @RequestBody StaffUpdateRequest request,
            @AuthenticationPrincipal LoginStaff loginStaff) {
        requireAdmin(loginStaff);
        return ResponseEntity.ok(masterAdminService.updateStaff(id, request));
    }

    @PatchMapping("/staff/{id}/deactivate")
    public ResponseEntity<Void> deactivateStaff(
            @PathVariable Long id, @AuthenticationPrincipal LoginStaff loginStaff) {
        requireAdmin(loginStaff);
        masterAdminService.deactivateStaff(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/exam-types")
    public ResponseEntity<ExamTypeResponse> createExamType(
            @Valid @RequestBody ExamTypeUpsertRequest request,
            @AuthenticationPrincipal LoginStaff loginStaff) {
        requireAdmin(loginStaff);
        return ResponseEntity.ok(masterAdminService.createExamType(request));
    }

    @PutMapping("/exam-types/{id}")
    public ResponseEntity<ExamTypeResponse> updateExamType(
            @PathVariable Long id,
            @Valid @RequestBody ExamTypeUpsertRequest request,
            @AuthenticationPrincipal LoginStaff loginStaff) {
        requireAdmin(loginStaff);
        return ResponseEntity.ok(masterAdminService.updateExamType(id, request));
    }

    @PatchMapping("/exam-types/{id}/deactivate")
    public ResponseEntity<Void> deactivateExamType(
            @PathVariable Long id, @AuthenticationPrincipal LoginStaff loginStaff) {
        requireAdmin(loginStaff);
        masterAdminService.deactivateExamType(id);
        return ResponseEntity.noContent().build();
    }

    private void requireAdmin(LoginStaff loginStaff) {
        if (loginStaff.role() != StaffRole.ADMIN) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_ROLE);
        }
    }
}
