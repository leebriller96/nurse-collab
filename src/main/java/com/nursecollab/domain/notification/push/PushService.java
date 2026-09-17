package com.nursecollab.domain.notification.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nursecollab.global.error.BusinessException;
import com.nursecollab.global.error.ErrorCode;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
public class PushService {

    private final PushSubscriptionRepository repository;
    private final WebPushSender sender;
    private final VapidKeys vapid;
    private final PushProperties properties;
    private final ObjectMapper objectMapper;

    /**
     * 보내기는 요청 스레드 밖에서 한다. 푸시 서비스가 느려도 접수 버튼은 바로 돌아와야 한다.
     * 기다리는 동안 하는 일이 전부 네트워크라 가상 스레드로 충분하다.
     */
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public PushService(PushSubscriptionRepository repository, WebPushSender sender, VapidKeys vapid,
                       PushProperties properties, ObjectMapper objectMapper) {
        this.repository = repository;
        this.sender = sender;
        this.vapid = vapid;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public String publicKey() {
        return vapid.publicKeyBase64Url();
    }

    @Transactional
    public void subscribe(Long staffId, String endpoint, String p256dh, String auth) {
        if (!PushEndpoints.allowed(endpoint, properties.allowedHosts())) {
            throw new BusinessException(ErrorCode.PUSH_ENDPOINT_NOT_ALLOWED);
        }
        requireDeviceKeys(p256dh, auth);

        repository.findByEndpoint(endpoint).ifPresentOrElse(
                existing -> existing.handOver(staffId, p256dh, auth),
                () -> repository.save(PushSubscription.of(staffId, endpoint, p256dh, auth)));
    }

    public void unsubscribe(Long staffId, String endpoint) {
        repository.deleteMine(endpoint, staffId);
    }

    /**
     * 알림함에 남긴 알림을 받는 사람들의 기기로 보낸다. 받는 사람은 알림함 규칙이 이미 골랐다.
     *
     * @param tag 같은 요청의 다음 알림이 앞의 것을 폰 알림창에서 덮게 한다. 한 요청에 알림이 여섯 개 쌓이면
     *            알림창이 요청 목록이 된다.
     */
    public void deliverAsync(Collection<Long> staffIds, String title, String body, String url, String tag,
                             boolean urgent) {
        if (!vapid.enabled() || staffIds.isEmpty()) return;

        var subscriptions = repository.findAllByStaffIdIn(staffIds);
        if (subscriptions.isEmpty()) return;

        byte[] payload = payload(title, body, url, tag);
        for (PushSubscription subscription : subscriptions) {
            executor.submit(() -> deliver(subscription, payload, urgent));
        }
    }

    private void deliver(PushSubscription subscription, byte[] payload, boolean urgent) {
        // 허용 목록이 좁아진 뒤에도 옛 구독이 남아 있을 수 있다. 보낼 때 다시 본다.
        if (!PushEndpoints.allowed(subscription.getEndpoint(), properties.allowedHosts())) return;
        try {
            var result = sender.send(subscription, payload, urgent);
            int status = result.status();
            log.debug("폰 알림 보냄. status={}, host={}", status, host(subscription));
            if (status == 404 || status == 410) {
                // 앱을 지웠거나 권한을 거둔 기기다. 남겨 두면 알림마다 헛걸음을 한다.
                repository.deleteByEndpoint(subscription.getEndpoint());
            } else if (status >= 400) {
                log.warn("폰 알림이 거절됐다. status={}, host={}, reason={}",
                        status, host(subscription), result.reason());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            // 한 기기가 안 닿아도 알림함에는 이미 남았다
            log.warn("폰 알림을 보내지 못했다. host={}", host(subscription), e);
        }
    }

    private byte[] payload(String title, String body, String url, String tag) {
        Map<String, String> json = new LinkedHashMap<>();
        json.put("title", title);
        json.put("body", body);
        json.put("url", url);
        json.put("tag", tag);
        try {
            return objectMapper.writeValueAsBytes(json);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** 브라우저가 만든 키가 망가져 있으면 보낼 때마다 실패한다. 등록할 때 막는다. */
    private static void requireDeviceKeys(String p256dh, String auth) {
        try {
            EcKeys.publicKey(Base64.getUrlDecoder().decode(p256dh));
            if (Base64.getUrlDecoder().decode(auth).length != 16) {
                throw new IllegalArgumentException("auth 는 16바이트다");
            }
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    /** 로그에 구독 주소를 통째로 남기지 않는다. 주소 자체가 그 기기로 보낼 수 있는 열쇠다. */
    private static String host(PushSubscription subscription) {
        return java.net.URI.create(subscription.getEndpoint()).getHost();
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }
}
