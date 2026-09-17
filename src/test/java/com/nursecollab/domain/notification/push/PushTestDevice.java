package com.nursecollab.domain.notification.push;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.util.Base64;

/**
 * 테스트용 가짜 기기. 브라우저가 구독할 때 만드는 키쌍과 인증 비밀을 흉내 내고,
 * 서버가 보낸 본문을 기기처럼 풀어 본다.
 */
public record PushTestDevice(KeyPair keys, byte[] authSecret) {

    public static PushTestDevice create() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(EcKeys.params());
        byte[] auth = new byte[16];
        new SecureRandom().nextBytes(auth);
        return new PushTestDevice(generator.generateKeyPair(), auth);
    }

    public String p256dh() {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(EcKeys.uncompressed((ECPublicKey) keys.getPublic()));
    }

    public String auth() {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(authSecret);
    }

    public byte[] decrypt(byte[] body) {
        return WebPushEncryption.decrypt(body, keys, authSecret);
    }
}
