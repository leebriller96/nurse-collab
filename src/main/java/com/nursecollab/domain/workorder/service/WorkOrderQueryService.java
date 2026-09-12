package com.nursecollab.domain.workorder.service;

import com.nursecollab.domain.patient.dto.AlertResponse;
import com.nursecollab.domain.patient.dto.ChecklistWarning;
import com.nursecollab.domain.patient.entity.PatientAlert;
import com.nursecollab.domain.patient.repository.PatientAlertRepository;
import com.nursecollab.domain.staff.entity.Staff;
import com.nursecollab.domain.staff.repository.StaffRepository;
import com.nursecollab.domain.workorder.dto.WorkOrderDetailResponse;
import com.nursecollab.domain.workorder.dto.OrderDirection;
import com.nursecollab.domain.workorder.dto.WorkOrderEventResponse;
import com.nursecollab.domain.workorder.dto.WorkOrderSummary;
import com.nursecollab.domain.workorder.entity.OrderPriority;
import com.nursecollab.domain.workorder.entity.WorkOrder;
import com.nursecollab.domain.workorder.entity.OrderStatus;
import com.nursecollab.domain.workorder.repository.WorkOrderEventRepository;
import com.nursecollab.domain.workorder.repository.WorkOrderRepository;
import com.nursecollab.global.common.PageResponse;
import com.nursecollab.global.error.BusinessException;
import com.nursecollab.global.error.ErrorCode;
import com.nursecollab.global.security.LoginStaff;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkOrderQueryService {

    private final WorkOrderRepository requestRepository;
    private final WorkOrderEventRepository eventRepository;
    private final PatientAlertRepository alertRepository;
    private final StaffRepository staffRepository;

    /**
     * 요청 목록.
     * direction 하나로 병동 화면과 검사실 화면을 같은 엔드포인트에서 처리한다.
     * 어느 컬럼으로 거를지는 클라이언트가 아니라 서버가 정한다.
     */
    /**
     * 요청 목록.
     * direction 하나로 병동 화면과 검사실 화면을 같은 엔드포인트에서 처리한다.
     * 어느 컬럼으로 거를지는 클라이언트가 아니라 서버가 정한다.
     *
     * 기간을 열어 두면 같은 엔드포인트가 지난 요청 검색(E-04)도 처리한다.
     * 화면마다 엔드포인트를 나누면 권한 검증과 조립 코드가 그만큼 흩어진다.
     */
    public PageResponse<WorkOrderSummary> search(OrderDirection direction,
                                                List<OrderStatus> statuses,
                                                LocalDate from, LocalDate to,
                                                OrderPriority priority,
                                                String keyword,
                                                Collection<UUID> subjectRefs,
                                                Pageable pageable,
                                                LoginStaff loginStaff) {

        LocalDate fromDate = (from == null) ? LocalDate.now() : from;
        LocalDate toDate = (to == null) ? fromDate : to;
        if (toDate.isBefore(fromDate)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        ZoneId zone = ZoneId.systemDefault();
        OffsetDateTime start = fromDate.atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime end = toDate.plusDays(1).atStartOfDay(zone).toOffsetDateTime();

        var page = requestRepository.search(
                direction.isInbound(), loginStaff.departmentId(),
                statusFilter(statuses), priority, start, end,
                (keyword == null || keyword.isBlank()) ? null : keyword.trim(),
                refFilter(subjectRefs), pageable);

        List<WorkOrder> requests = page.getContent();
        if (requests.isEmpty()) {
            return new PageResponse<>(List.of(), page.getNumber(), page.getSize(),
                    page.getTotalElements(), page.getTotalPages());
        }

        // 주의사항 개수는 여기서 세지 않는다. 그건 진료정보라 원내에 있다.
        // 화면이 subjectRef 로 원내에 물어 빨간 표시를 붙인다.
        return PageResponse.of(page.map(request ->
                WorkOrderSummary.of(request, direction.isInbound())));
    }

    /**
     * 빈 컬렉션을 in 절에 바인딩하면 Hibernate 가 조건을 만들지 못한다.
     * 아무것도 맞지 않는 값 하나를 넣어 "가명으로는 걸리는 것이 없다" 를 표현한다.
     */
    private static Collection<UUID> refFilter(Collection<UUID> subjectRefs) {
        return (subjectRefs == null || subjectRefs.isEmpty())
                ? List.of(new UUID(0L, 0L))
                : subjectRefs;
    }

    public WorkOrderDetailResponse findDetail(Long requestId, LoginStaff loginStaff) {
        WorkOrder request = requestRepository.findDetailById(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REQUEST_NOT_FOUND));

        Staff viewer = staffRepository.findByIdWithDepartment(loginStaff.staffId())
                .orElseThrow(() -> new BusinessException(ErrorCode.STAFF_NOT_FOUND));

        // 관계없는 파트면 여기서 막힌다
        request.resolveActorSide(viewer);

        // 주의사항과 확인 경고는 이 응답에 없다. 진료정보라 원내에서 온다.
        return WorkOrderDetailResponse.of(request, viewer);
    }

    public List<WorkOrderEventResponse> findEvents(Long requestId, LoginStaff loginStaff) {
        WorkOrder request = requestRepository.findByIdWithDepartments(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REQUEST_NOT_FOUND));

        Staff viewer = staffRepository.findByIdWithDepartment(loginStaff.staffId())
                .orElseThrow(() -> new BusinessException(ErrorCode.STAFF_NOT_FOUND));

        request.resolveActorSide(viewer);

        return eventRepository.findAllByRequestId(requestId)
                .stream().map(WorkOrderEventResponse::from).toList();
    }

    /** 미지정이면 진행중 전체를 본다 */
    private Collection<OrderStatus> statusFilter(List<OrderStatus> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            EnumSet<OrderStatus> inProgress = EnumSet.allOf(OrderStatus.class);
            inProgress.removeAll(OrderStatus.terminals());
            return inProgress;
        }
        return statuses;
    }
}
