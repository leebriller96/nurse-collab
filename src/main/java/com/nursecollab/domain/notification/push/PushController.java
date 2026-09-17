package com.nursecollab.domain.notification.push;

import com.nursecollab.global.security.LoginStaff;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/push")
@RequiredArgsConstructor
@EnableConfigurationProperties(PushProperties.class)
public class PushController {

    private final PushService pushService;

    /** 키가 없으면 null. 화면은 "이 기기에서 알림 받기" 버튼을 감춘다. */
    @GetMapping("/public-key")
    public ResponseEntity<PublicKeyResponse> publicKey() {
        return ResponseEntity.ok(new PublicKeyResponse(pushService.publicKey()));
    }

    /** 본문은 브라우저 PushSubscription.toJSON() 그대로다 */
    @PostMapping("/subscriptions")
    public ResponseEntity<Void> subscribe(@Valid @RequestBody SubscribeRequest request,
                                          @AuthenticationPrincipal LoginStaff loginStaff) {
        pushService.subscribe(loginStaff.staffId(), request.endpoint(),
                request.keys().p256dh(), request.keys().auth());
        return ResponseEntity.noContent().build();
    }

    /** 로그아웃할 때 화면이 부른다. 돌려 쓰는 병동 폰에서 앞사람 알림이 남지 않게. */
    @DeleteMapping("/subscriptions")
    public ResponseEntity<Void> unsubscribe(@Valid @RequestBody UnsubscribeRequest request,
                                            @AuthenticationPrincipal LoginStaff loginStaff) {
        pushService.unsubscribe(loginStaff.staffId(), request.endpoint());
        return ResponseEntity.noContent().build();
    }

    public record PublicKeyResponse(String publicKey) {}

    public record SubscribeRequest(@NotBlank String endpoint, @NotNull @Valid Keys keys) {
        public record Keys(@NotBlank String p256dh, @NotBlank String auth) {}
    }

    public record UnsubscribeRequest(@NotBlank String endpoint) {}
}
