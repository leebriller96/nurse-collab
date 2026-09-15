package com.nursecollab.domain.master.controller;

import com.nursecollab.domain.department.dto.DepartmentResponse;
import com.nursecollab.domain.master.dto.DepartmentUpsertRequest;
import com.nursecollab.domain.master.dto.ServiceItemUpsertRequest;
import com.nursecollab.domain.master.dto.StaffAdminResponse;
import com.nursecollab.domain.master.dto.StaffCreateRequest;
import com.nursecollab.domain.master.dto.StaffUpdateRequest;
import com.nursecollab.domain.master.service.MasterAdminService;
import com.nursecollab.domain.staff.entity.StaffRole;
import com.nursecollab.domain.workorder.dto.ServiceItemResponse;
import com.nursecollab.global.audit.Audited;
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
 *
 * 변경은 전부 업무 감사 기록에 남는다. 직원 수정만 서비스가 전후를 직접 적는다 —
 * 역할과 소속을 누가 무엇에서 무엇으로 바꿨는지가 이 기록이 답해야 할 첫 질문이다.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class MasterAdminController {

    private final MasterAdminService masterAdminService;

    @PostMapping("/departments")
    @Audited(action = "CREATE", targetType = "DEPARTMENT")
    public ResponseEntity<DepartmentResponse> createDepartment(
            @Valid @RequestBody DepartmentUpsertRequest request,
            @AuthenticationPrincipal LoginStaff loginStaff) {
        requireAdmin(loginStaff);
        return ResponseEntity.ok(masterAdminService.createDepartment(request));
    }

    @PutMapping("/departments/{id}")
    @Audited(action = "UPDATE", targetType = "DEPARTMENT")
    public ResponseEntity<DepartmentResponse> updateDepartment(
            @PathVariable Long id,
            @Valid @RequestBody DepartmentUpsertRequest request,
            @AuthenticationPrincipal LoginStaff loginStaff) {
        requireAdmin(loginStaff);
        return ResponseEntity.ok(masterAdminService.updateDepartment(id, request));
    }

    @PatchMapping("/departments/{id}/deactivate")
    @Audited(action = "DEACTIVATE", targetType = "DEPARTMENT")
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
    @Audited(action = "CREATE", targetType = "STAFF")
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
        return ResponseEntity.ok(masterAdminService.updateStaff(id, request, loginStaff.staffId()));
    }

    @PatchMapping("/staff/{id}/deactivate")
    @Audited(action = "DEACTIVATE", targetType = "STAFF")
    public ResponseEntity<Void> deactivateStaff(
            @PathVariable Long id, @AuthenticationPrincipal LoginStaff loginStaff) {
        requireAdmin(loginStaff);
        masterAdminService.deactivateStaff(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/service-items")
    @Audited(action = "CREATE", targetType = "SERVICE_ITEM")
    public ResponseEntity<ServiceItemResponse> createServiceItem(
            @Valid @RequestBody ServiceItemUpsertRequest request,
            @AuthenticationPrincipal LoginStaff loginStaff) {
        requireAdmin(loginStaff);
        return ResponseEntity.ok(masterAdminService.createServiceItem(request));
    }

    @PutMapping("/service-items/{id}")
    @Audited(action = "UPDATE", targetType = "SERVICE_ITEM")
    public ResponseEntity<ServiceItemResponse> updateServiceItem(
            @PathVariable Long id,
            @Valid @RequestBody ServiceItemUpsertRequest request,
            @AuthenticationPrincipal LoginStaff loginStaff) {
        requireAdmin(loginStaff);
        return ResponseEntity.ok(masterAdminService.updateServiceItem(id, request));
    }

    @PatchMapping("/service-items/{id}/deactivate")
    @Audited(action = "DEACTIVATE", targetType = "SERVICE_ITEM")
    public ResponseEntity<Void> deactivateServiceItem(
            @PathVariable Long id, @AuthenticationPrincipal LoginStaff loginStaff) {
        requireAdmin(loginStaff);
        masterAdminService.deactivateServiceItem(id);
        return ResponseEntity.noContent().build();
    }

    private void requireAdmin(LoginStaff loginStaff) {
        if (loginStaff.role() != StaffRole.ADMIN) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_ROLE);
        }
    }
}
