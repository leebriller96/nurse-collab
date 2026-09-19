package com.nursecollab.domain.notification.push;

import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

/**
 * 푸시 서비스에 "우리가 보낸 것" 임을 증명하는 키(VAPID, RFC 8292).
 *
 * 브라우저는 구독할 때 이 공개키를 받아 묶어 둔다. 다른 키로 서명한 푸시는 서비스가 거절한다.
 * 그래서 키를 바꾸면 기존 구독이 전부 무효가 된다 — JWT 키처럼 가볍게 돌리지 않는다.
 *
 * 키가 없으면 폰 알림이 꺼진다. 서버는 그대로 뜬다. 폰 알림이 없어도 알림함과 실시간 채널은 돈다.
 */
@Slf4j
@Component
public class VapidKeys {

    private static final Base64.Encoder B64URL = Base64.getUrlEncoder().withoutPadding();

    private final ECPrivateKey privateKey;
    private final ECPublicKey publicKey;
    private final String subject;

    public VapidKeys(PushProperties properties, ResourceLoader loader) {
        this.subject = properties.subject();
        if (isBlank(properties.vapidPrivateKey()) || isBlank(properties.vapidPublicKey())) {
            this.privateKey = null;
            this.publicKey = null;
            log.info("VAPID 키가 없어 폰 알림을 끈다. 알림함과 실시간 채널은 그대로 돈다");
            return;
        }
        try {
            this.privateKey = (ECPrivateKey) KeyFactory.getInstance("EC").generatePrivate(
                    new PKCS8EncodedKeySpec(der(loader.getResource(properties.vapidPrivateKey()), "PRIVATE KEY")));
            this.publicKey = (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(
                    new X509EncodedKeySpec(der(loader.getResource(properties.vapidPublicKey()), "PUBLIC KEY")));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("VAPID 키를 읽지 못했다 — P-256 PKCS#8 개인키와 그 공개키여야 한다", e);
        }
        requirePair();
    }

    public boolean enabled() {
        return privateKey != null;
    }

    /** 브라우저 pushManager.subscribe 의 applicationServerKey. 날 점을 base64url 로. */
    public String publicKeyBase64Url() {
        return enabled() ? B64URL.encodeToString(EcKeys.uncompressed(publicKey)) : null;
    }

    /**
     * Authorization 헤더 값. aud 는 푸시 서비스의 오리진이어야 한다 — 다른 서비스로 옮겨 쓰지 못하게.
     * 유효시간은 12시간이다(RFC 8292 가 24시간을 넘지 말라고 한다). 보낼 때마다 새로 만든다.
     */
    String authorization(URI endpoint) {
        String audience = endpoint.getScheme() + "://" + endpoint.getAuthority();
        String jwt = Jwts.builder()
                .header().type("JWT").and()
                // 배열이 아니라 문자열이어야 한다. add() 는 ["..."] 로 적고, FCM 은 그걸 403 으로 튕긴다
                // (실제 크롬 구독으로 보내 보고서야 드러났다).
                .audience().single(audience)
                .expiration(Date.from(Instant.now().plusSeconds(12 * 3600)))
                .subject(subject)
                .signWith(privateKey, Jwts.SIG.ES256)
                .compact();
        return "vapid t=" + jwt + ", k=" + publicKeyBase64Url();
    }

    /** 두 파일이 짝이 아니면 푸시가 전부 401 로 튕기는데, 그건 첫 알림이 나갈 때에야 드러난다. 뜰 때 막는다. */
    private void requirePair() {
        try {
            byte[] probe = "vapid-pair-check".getBytes(StandardCharsets.US_ASCII);
            Signature signer = Signature.getInstance("SHA256withECDSA");
            signer.initSign(privateKey);
            signer.update(probe);
            byte[] signature = signer.sign();
            Signature verifier = Signature.getInstance("SHA256withECDSA");
            verifier.initVerify(publicKey);
            verifier.update(probe);
            if (!verifier.verify(signature)) {
                throw new IllegalStateException("VAPID 개인키와 공개키가 짝이 아니다");
            }
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("VAPID 키 확인 실패", e);
        }
    }

    private static byte[] der(Resource resource, String label) {
        if (!resource.exists()) {
            throw new IllegalStateException("VAPID 키 파일을 찾을 수 없다: " + resource.getDescription());
        }
        try (var in = resource.getInputStream()) {
            String body = new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("-----BEGIN " + label + "-----", "")
                    .replace("-----END " + label + "-----", "")
                    .replaceAll("\\s", "");
            return Base64.getDecoder().decode(body);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
