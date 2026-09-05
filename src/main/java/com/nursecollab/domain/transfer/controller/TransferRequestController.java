package com.nursecollab.domain.transfer.controller;

import com.nursecollab.domain.transfer.dto.MessageCreateRequest;
import com.nursecollab.domain.transfer.dto.MessageResponse;
import com.nursecollab.domain.transfer.dto.TransferCreateRequest;
import com.nursecollab.domain.transfer.dto.TransferCreateResponse;
import com.nursecollab.domain.transfer.dto.TransferDetailResponse;
import com.nursecollab.domain.transfer.dto.TransferDirection;
import com.nursecollab.domain.transfer.dto.TransferEventResponse;
import com.nursecollab.domain.transfer.dto.TransferSummary;
import com.nursecollab.domain.transfer.dto.TransitionRequest;
import com.nursecollab.domain.transfer.dto.TransitionResponse;
import com.nursecollab.domain.transfer.entity.TransferPriority;
import com.nursecollab.domain.transfer.entity.TransferStatus;
import com.nursecollab.domain.transfer.service.RequestMessageService;
import com.nursecollab.domain.transfer.service.TransferQueryService;
import com.nursecollab.domain.transfer.service.TransferRequestService;
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

@RestController
@RequestMapping("/api/v1/transfer-requests")
@RequiredArgsConstructor
public class TransferRequestController {

    private final TransferRequestService transferRequestService;
    private final TransferQueryService transferQueryService;
    private final RequestMessageService requestMessageService;

    /** 이송 요청 생성 */
    @PostMapping
    public ResponseEntity<TransferCreateResponse> create(
            @Valid @RequestBody TransferCreateRequest request,
            @AuthenticationPrincipal LoginStaff loginStaff) {

        TransferCreateResponse response =
                transferRequestService.create(request, loginStaff.staffId());
        return ResponseEntity
                .created(URI.create("/api/v1/transfer-requests/" + response.id()))
                .body(response);
    }

    /** 요청 목록. direction 하나로 병동 현황과 검사실 큐를 모두 처리한다. */
    @GetMapping
    public ResponseEntity<PageResponse<TransferSummary>> search(
            @RequestParam TransferDirection direction,
            @RequestParam(required = false) List<TransferStatus> status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) TransferPriority priority,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal LoginStaff loginStaff) {

        return ResponseEntity.ok(transferQueryService.search(
                direction, status, date, priority, PageRequest.of(page, size), loginStaff));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransferDetailResponse> detail(
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
                transferRequestService.transition(id, request, loginStaff.staffId()));
    }

    /** 타임라인 */
    @GetMapping("/{id}/events")
    public ResponseEntity<List<TransferEventResponse>> events(
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
                .created(URI.create("/api/v1/transfer-requests/" + id + "/messages/" + response.id()))
                .body(response);
    }
}
