package com.nursecollab.domain.notification.push;

import java.net.URI;
import java.util.List;
import java.util.Locale;

/**
 * 보내도 되는 구독 주소인가.
 *
 * 구독 주소는 브라우저가 보내온 값이다. 그대로 믿으면 로그인한 누구든 서버에게
 * 내부망 주소로 요청을 쏘게 만들 수 있다(SSRF). 알려진 푸시 서비스만 연다.
 */
final class PushEndpoints {

    private PushEndpoints() {}

    static boolean allowed(String endpoint, List<String> allowedHosts) {
        URI uri;
        try {
            uri = URI.create(endpoint);
        } catch (IllegalArgumentException e) {
            return false;
        }
        String host = uri.getHost();
        if (host == null || uri.getUserInfo() != null) return false;
        host = host.toLowerCase(Locale.ROOT);

        boolean local = host.equals("localhost");
        // 평문은 테스트가 localhost 에 띄운 가짜 푸시 서비스만. 운영 목록에는 localhost 가 없다.
        if (!"https".equals(uri.getScheme()) && !(local && "http".equals(uri.getScheme()))) return false;

        for (String pattern : allowedHosts) {
            String p = pattern.trim().toLowerCase(Locale.ROOT);
            if (p.isEmpty()) continue;
            if (p.startsWith("*.") ? host.endsWith(p.substring(1)) : host.equals(p)) return true;
        }
        return false;
    }
}
