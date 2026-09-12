package com.nursecollab.domain.workorder.controller;

import com.nursecollab.domain.workorder.dto.MessageCreateRequest;
import com.nursecollab.domain.workorder.dto.MessageResponse;
import com.nursecollab.domain.workorder.dto.WorkOrderCreateRequest;
import com.nursecollab.domain.workorder.dto.WorkOrderCreateResponse;
import com.nursecollab.domain.workorder.dto.WorkOrderDetailResponse;
import com.nursecollab.domain.workorder.dto.OrderDirection;
import com.nursecollab.domain.workorder.dto.WorkOrderEventResponse;
import com.nursecollab.domain.workorder.dto.WorkOrderSummary;
import com.nursecollab.domain.workorder.dto.TransitionRequest;
import com.nursecollab.domain.workorder.dto.TransitionResponse;
import com.nursecollab.domain.workorder.entity.OrderPriority;
import com.nursecollab.domain.workorder.entity.OrderStatus;
import com.nursecollab.domain.workorder.service.RequestMessageService;
import com.nursecollab.domain.workorder.service.WorkOrderQueryService;
import com.nursecollab.domain.workorder.service.WorkOrderService;
import com.nursecollab.global.common.PageResponse;
import com.nursecollab.global.security.LoginStaff;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/work-orders")
@RequiredArgsConstructor
public class WorkOrderController {

    private final WorkOrderService workOrderService;
    private final WorkOrderQueryService transferQueryService;
    private final RequestMessageService requestMessageService;

    /** 업무 요청 생성 */
    @PostMapping
    public ResponseEntity<WorkOrderCreateResponse> create(
            @Valid @RequestBody WorkOrderCreateRequest request,
            @AuthenticationPrincipal LoginStaff loginStaff) {

        WorkOrderCreateResponse response =
                workOrderService.create(request, loginStaff.staffId());
        return ResponseEntity
                .created(URI.create("/api/v1/work-orders/" + response.id()))
                .body(response);
    }

    /** 요청 목록. direction 하나로 병동 현황과 검사실 큐를 모두 처리한다. */
    @GetMapping
    public ResponseEntity<PageResponse<WorkOrderSummary>> search(
            @RequestParam OrderDirection direction,
            @RequestParam(required = false) List<OrderStatus> status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String keyword,
            // 이름으로 찾은 결과다. 부르는 쪽이 원내에 먼저 물어 가명 목록을 받아 넘긴다.
            // 업무 쪽에는 이름이 없으므로 여기서 이름으로 거를 방법이 없다.
            @RequestParam(required = false) List<UUID> subjectRefs,
            @RequestParam(required = false) OrderPriority priority,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal LoginStaff loginStaff) {

        return ResponseEntity.ok(transferQueryService.search(
                direction, status, from, to, priority, keyword, subjectRefs,
                PageRequest.of(page, size), loginStaff));
    }

    @GetMapping("/{id}")
    public ResponseEntity<WorkOrderDetailResponse> detail(
            @PathVariable Long id,
            @AuthenticationPrincipal LoginStaff loginStaff) {
        return ResponseEntity.ok(transferQueryService.findDetail(id, loginStaff));
    }

    /** 상태 전이 (접수/준비완료/이송중/보류/취소 전부 이 하나로 처리) */
    @PostMapping("/{id}/transitions")
    public ResponseEntity<TransitionResponse> transition(
            @PathVariable Long id,
            @Valid @RequestBody TransitionRequest request,
            @AuthenticationPrincipal LoginStaff loginStaff) {

        return ResponseEntity.ok(
                workOrderService.transition(id, request, loginStaff.staffId()));
    }

    /** 타임라인 */
    @GetMapping("/{id}/events")
    public ResponseEntity<List<WorkOrderEventResponse>> events(
            @PathVariable Long id,
            @AuthenticationPrincipal LoginStaff loginStaff) {
        return ResponseEntity.ok(transferQueryService.findEvents(id, loginStaff));
    }

    @GetMapping("/{id}/messages")
    public ResponseEntity<List<MessageResponse>> messages(
            @PathVariable Long id,
            @AuthenticationPrincipal LoginStaff loginStaff) {
        return ResponseEntity.ok(requestMessageService.findAll(id, loginStaff.staffId()));
    }

    @PostMapping("/{id}/messages")
    public ResponseEntity<MessageResponse> createMessage(
            @PathVariable Long id,
            @Valid @RequestBody MessageCreateRequest request,
            @AuthenticationPrincipal LoginStaff loginStaff) {

        MessageResponse response = requestMessageService.create(id, request, loginStaff.staffId());
        return ResponseEntity
                .created(URI.create("/api/v1/work-orders/" + id + "/messages/" + response.id()))
                .body(response);
    }
}
