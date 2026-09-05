package com.nursecollab.domain.notification.controller;

import com.nursecollab.domain.notification.dto.NotificationsResponse;
import com.nursecollab.domain.notification.service.NotificationService;
import com.nursecollab.global.security.LoginStaff;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<NotificationsResponse> find(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal LoginStaff loginStaff) {
        return ResponseEntity.ok(notificationService.find(
                loginStaff.staffId(), unreadOnly, PageRequest.of(page, size)));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable Long id,
                                         @AuthenticationPrincipal LoginStaff loginStaff) {
        notificationService.markRead(id, loginStaff.staffId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllRead(@AuthenticationPrincipal LoginStaff loginStaff) {
        notificationService.markAllRead(loginStaff.staffId());
        return ResponseEntity.noContent().build();
    }
}
