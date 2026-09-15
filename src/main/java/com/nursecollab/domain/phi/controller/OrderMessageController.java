package com.nursecollab.domain.phi.controller;

import com.nursecollab.domain.phi.dto.MessageBodyCreated;
import com.nursecollab.domain.phi.dto.MessageBodyRequest;
import com.nursecollab.domain.phi.dto.MessageBodyResponse;
import com.nursecollab.domain.phi.service.OrderMessageService;
import com.nursecollab.global.security.LoginStaff;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * W-05, E-02 의 대화 내용. 누가·언제는 업무 쪽 {@code /work-orders/{id}/messages} 에 있다.
 *
 * 경로가 {@code /phi} 아래인 이유는 활력징후와 같다. 중계 서버가 이 앞머리만 보고 원내로 보낸다.
 */
@RestController
@RequestMapping("/api/v1/phi/work-orders/{orderId}/messages")
@RequiredArgsConstructor
public class OrderMessageController {

    private final OrderMessageService orderMessageService;

    @PostMapping
    public ResponseEntity<MessageBodyCreated> write(
            @PathVariable Long orderId,
            @Valid @RequestBody MessageBodyRequest request,
            @AuthenticationPrincipal LoginStaff loginStaff) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(orderMessageService.write(orderId, request, loginStaff));
    }

    @GetMapping("/bodies")
    public ResponseEntity<List<MessageBodyResponse>> bodies(
            @PathVariable Long orderId,
            @AuthenticationPrincipal LoginStaff loginStaff) {
        return ResponseEntity.ok(orderMessageService.bodies(orderId, loginStaff));
    }
}
