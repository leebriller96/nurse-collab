package com.nursecollab.domain.notification.push;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * 폰 알림 설정.
 *
 * @param vapidPrivateKey 서명용 개인키 파일(PKCS#8 PEM). 비우면 폰 알림이 꺼진다.
 *                        키는 JWT 처럼 환경변수가 아니라 파일로 넘긴다.
 * @param vapidPublicKey  위와 짝인 공개키 파일. JDK 는 EC 개인키에서 공개키를 꺼내 주지 않아 따로 받는다.
 * @param subject         푸시 서비스가 문제 있을 때 연락할 곳(mailto:). 애플은 없으면 거절한다.
 * @param allowedHosts    보내도 되는 푸시 서비스. "*.example.com" 은 하위 도메인까지.
 *                        구독 주소는 브라우저가 보낸 값이라 그대로 믿으면 서버가 아무 곳에나 요청을 쏜다.
 * @param ttl             푸시 서비스가 기기가 꺼져 있을 때 들고 있을 시간.
 */
@ConfigurationProperties(prefix = "app.push")
public record PushProperties(
        String vapidPrivateKey,
        String vapidPublicKey,
        String subject,
        List<String> allowedHosts,
        Duration ttl
) {
    public PushProperties {
        allowedHosts = allowedHosts == null ? List.of() : allowedHosts;
        // 한 시간 지난 "접수됐습니다" 는 폰에 떠도 쓸모가 없고, 지금 상태로 오해하게 만든다
        ttl = ttl == null ? Duration.ofHours(1) : ttl;
    }
}
