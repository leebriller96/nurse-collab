package com.nursecollab.domain.master.service;

import com.nursecollab.domain.department.dto.DepartmentResponse;
import com.nursecollab.domain.department.entity.Department;
import com.nursecollab.domain.department.repository.DepartmentRepository;
import com.nursecollab.domain.master.dto.DepartmentUpsertRequest;
import com.nursecollab.domain.master.dto.ServiceItemUpsertRequest;
import com.nursecollab.domain.master.dto.StaffAdminResponse;
import com.nursecollab.domain.master.dto.StaffCreateRequest;
import com.nursecollab.domain.master.dto.StaffUpdateRequest;
import com.nursecollab.domain.staff.entity.Staff;
import com.nursecollab.domain.staff.repository.StaffRepository;
import com.nursecollab.domain.workorder.dto.ServiceItemResponse;
import com.nursecollab.domain.workorder.entity.ServiceItem;
import com.nursecollab.domain.workorder.repository.ServiceItemRepository;
import com.nursecollab.global.audit.AuditRecorder;
import com.nursecollab.global.error.BusinessException;
import com.nursecollab.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 부서·직원·업무 항목 관리.
 *
 * 어느 것도 지우지 않는다. 부서나 업무 항목을 삭제하면 그것을 참조하던
 * 지난 요청의 이력을 읽을 수 없게 된다. 기록은 남기고 새로 고르지만 못하게 한다.
 */
@Service
@RequiredArgsConstructor
public class MasterAdminService {

    private final DepartmentRepository departmentRepository;
    private final StaffRepository staffRepository;
    private final ServiceItemRepository serviceItemRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditRecorder auditRecorder;

    // ------------------------------------------------------------------
    // 부서
    // ------------------------------------------------------------------

    @Transactional
    public DepartmentResponse createDepartment(DepartmentUpsertRequest req) {
        if (departmentRepository.existsByCode(req.code())) {
            throw new BusinessException(ErrorCode.DUPLICATE_CODE);
        }
        Department saved = departmentRepository.save(Department.create(
                req.code(), req.name(), req.deptType(), req.location(), req.phone()));
        return DepartmentResponse.from(saved);
    }

    @Transactional
    public DepartmentResponse updateDepartment(Long id, DepartmentUpsertRequest req) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.MASTER_NOT_FOUND));

        // 코드는 다른 부서가 쓰고 있지 않을 때만 바꿀 수 있다
        if (!department.getCode().equals(req.code()) && departmentRepository.existsByCode(req.code())) {
            throw new BusinessException(ErrorCode.DUPLICATE_CODE);
        }
        department.update(req.code(), req.name(), req.deptType(), req.location(), req.phone());
        return DepartmentResponse.from(department);
    }

    @Transactional
    public void deactivateDepartment(Long id) {
        departmentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.MASTER_NOT_FOUND))
                .deactivate();
    }

    // ------------------------------------------------------------------
    // 직원
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<StaffAdminResponse> findAllStaff() {
        return staffRepository.findAllWithDepartment().stream()
                .map(StaffAdminResponse::from).toList();
    }

    @Transactional
    public StaffAdminResponse createStaff(StaffCreateRequest req) {
        if (staffRepository.existsByLoginId(req.loginId())
                || staffRepository.existsByEmployeeNo(req.employeeNo())) {
            throw new BusinessException(ErrorCode.DUPLICATE_CODE);
        }
        Department department = departmentRepository.findById(req.departmentId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MASTER_NOT_FOUND));

        Staff saved = staffRepository.save(Staff.create(
                req.loginId(), passwordEncoder.encode(req.password()), req.employeeNo(),
                req.name(), req.role(), department, req.phone()));
        return StaffAdminResponse.from(saved);
    }

    /**
     * 비밀번호는 여기서 다루지 않는다.
     * 관리자가 남의 비밀번호를 아무 때나 바꿀 수 있으면 그 계정으로 한 일을 본인이 했다고 말할 수 없다.
     *
     * 바뀐 것이 있으면 전후를 감사 기록에 남긴다. 저장을 먼저 확정한 뒤 남긴다 —
     * 기록은 따로 커밋되므로, 순서가 바뀌면 실패한 수정이 "바꿨다" 로 남는다.
     */
    @Transactional
    public StaffAdminResponse updateStaff(Long id, StaffUpdateRequest req, Long actorId) {
        Staff staff = staffRepository.findByIdWithDepartment(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.MASTER_NOT_FOUND));
        Department department = departmentRepository.findById(req.departmentId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MASTER_NOT_FOUND));

        Map<String, Object> before = profileOf(staff);
        staff.updateProfile(req.name(), req.role(), department, req.phone());
        staffRepository.flush();
        Map<String, Object> after = profileOf(staff);

        if (!before.equals(after)) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("before", before);
            detail.put("after", after);
            auditRecorder.recordRequest(actorId, "UPDATE", "STAFF", id, detail);
        }
        return StaffAdminResponse.from(staff);
    }

    private static Map<String, Object> profileOf(Staff staff) {
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("name", staff.getName());
        profile.put("role", staff.getRole().name());
        profile.put("departmentId", staff.getDepartment().getId());
        profile.put("departmentName", staff.getDepartment().getName());
        profile.put("phone", staff.getPhone());
        return profile;
    }

    @Transactional
    public void deactivateStaff(Long id) {
        staffRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.MASTER_NOT_FOUND))
                .deactivate();
    }

    // ------------------------------------------------------------------
    // 업무 항목
    // ------------------------------------------------------------------

    @Transactional
    public ServiceItemResponse createServiceItem(ServiceItemUpsertRequest req) {
        if (serviceItemRepository.existsByCode(req.code())) {
            throw new BusinessException(ErrorCode.DUPLICATE_CODE);
        }
        Department department = departmentRepository.findById(req.departmentId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MASTER_NOT_FOUND));

        ServiceItem saved = serviceItemRepository.save(ServiceItem.create(
                req.code(), req.name(), req.orderType(), department, req.defaultDuration(),
                req.prepInstruction(), req.requiredAlerts()));
        return ServiceItemResponse.from(saved);
    }

    @Transactional
    public ServiceItemResponse updateServiceItem(Long id, ServiceItemUpsertRequest req) {
        ServiceItem serviceItem = serviceItemRepository.findByIdWithDepartment(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.MASTER_NOT_FOUND));

        if (!serviceItem.getCode().equals(req.code()) && serviceItemRepository.existsByCode(req.code())) {
            throw new BusinessException(ErrorCode.DUPLICATE_CODE);
        }
        Department department = departmentRepository.findById(req.departmentId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MASTER_NOT_FOUND));

        serviceItem.update(req.code(), req.name(), department, req.defaultDuration(),
                req.prepInstruction(), req.requiredAlerts());
        return ServiceItemResponse.from(serviceItem);
    }

    @Transactional
    public void deactivateServiceItem(Long id) {
        serviceItemRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.MASTER_NOT_FOUND))
                .deactivate();
    }
}
