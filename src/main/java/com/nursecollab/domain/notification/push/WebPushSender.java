package com.nursecollab.domain.notification.push;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;

/** 한 기기에 한 번 보낸다. 받은 상태 코드를 그대로 돌려준다 — 죽은 구독을 지울지는 부르는 쪽이 정한다. */
@Slf4j
@Component
class WebPushSender {

    private static final Base64.Decoder B64URL = Base64.getUrlDecoder();

    private final VapidKeys vapid;
    private final PushProperties properties;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            // 리다이렉트를 따라가면 허용 목록 검사를 우회한다
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    WebPushSender(VapidKeys vapid, PushProperties properties) {
        this.vapid = vapid;
        this.properties = properties;
    }

    /** 상태 코드와, 거절됐을 때 푸시 서비스가 준 까닭(앞부분만) */
    record Result(int status, String reason) {}

    Result send(PushSubscription subscription, byte[] payload, boolean urgent) throws Exception {
        URI endpoint = URI.create(subscription.getEndpoint());
        byte[] body = WebPushEncryption.encrypt(payload,
                B64URL.decode(subscription.getP256dh()), B64URL.decode(subscription.getAuth()));

        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(10))
                .header("Content-Encoding", "aes128gcm")
                .header("Content-Type", "application/octet-stream")
                .header("TTL", String.valueOf(properties.ttl().toSeconds()))
                // 배터리 절약 중인 폰은 normal 을 미룰 수 있다. 응급과 접수 지연은 미루면 안 된다.
                .header("Urgency", urgent ? "high" : "normal")
                .header("Authorization", vapid.authorization(endpoint))
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        // 거절 사유를 남기지 않으면 "403" 만 보고 키·서명·aud 중 무엇이 틀렸는지 알 수 없다
        String reason = response.statusCode() >= 400 && response.body() != null
                ? response.body().substring(0, Math.min(200, response.body().length()))
                : null;
        return new Result(response.statusCode(), reason);
    }
}
