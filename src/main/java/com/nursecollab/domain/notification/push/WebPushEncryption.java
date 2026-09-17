package com.nursecollab.domain.notification.push;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.util.Arrays;

/**
 * 웹 푸시 본문 암호화 (RFC 8291, Content-Encoding: aes128gcm 의 한 레코드).
 *
 * 본문은 구글·애플의 푸시 중계 서버를 지난다. 기기의 공개키로 암호화해서 중계 서버는 읽지 못한다.
 * 그래도 알림 문구에는 이름을 싣지 않는다 — 암호화는 중계를 믿지 않아도 되게 하는 것이지
 * 싣는 것을 늘려도 된다는 뜻이 아니다.
 *
 * 라이브러리를 쓰지 않은 이유와 믿는 근거는 {@code WebPushEncryptionTest} 에 있다.
 */
public final class WebPushEncryption {

    /** 한 레코드에 다 들어가야 한다. 푸시 서비스는 4KB 넘는 본문을 거절한다. */
    private static final int RECORD_SIZE = 4096;
    private static final SecureRandom RANDOM = new SecureRandom();

    private WebPushEncryption() {}

    /** 실제 발송용. 매번 새 임시키와 salt 를 쓴다. */
    public static byte[] encrypt(byte[] plaintext, byte[] uaPublic, byte[] authSecret) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return encrypt(plaintext, uaPublic, authSecret, ephemeralKeyPair(), salt);
    }

    /** 임시키와 salt 를 받는다. 표준 시험값에 맞춰 보려고 열어 둔다. */
    static byte[] encrypt(byte[] plaintext, byte[] uaPublic, byte[] authSecret, KeyPair server, byte[] salt) {
        if (plaintext.length + 1 + 16 > RECORD_SIZE - 86) {
            throw new IllegalArgumentException("푸시 본문이 너무 길다: " + plaintext.length + "바이트");
        }
        byte[] asPublic = EcKeys.uncompressed((ECPublicKey) server.getPublic());
        byte[][] keyAndNonce = deriveKeyAndNonce(
                ecdh(server, EcKeys.publicKey(uaPublic)), authSecret, uaPublic, asPublic, salt);

        // 마지막 레코드라는 표시(0x02)를 붙인다. 채움은 넣지 않는다.
        byte[] padded = Arrays.copyOf(plaintext, plaintext.length + 1);
        padded[plaintext.length] = 0x02;
        byte[] ciphertext = aesGcm(Cipher.ENCRYPT_MODE, keyAndNonce[0], keyAndNonce[1], padded);

        ByteBuffer body = ByteBuffer.allocate(16 + 4 + 1 + 65 + ciphertext.length);
        body.put(salt).putInt(RECORD_SIZE).put((byte) 65).put(asPublic).put(ciphertext);
        return body.array();
    }

    /** 기기 쪽 풀기. 서버는 쓰지 않는다 — 발송 경로가 맞는지 시험에서 되짚어 보려고 둔다. */
    static byte[] decrypt(byte[] body, KeyPair device, byte[] authSecret) {
        ByteBuffer in = ByteBuffer.wrap(body);
        byte[] salt = new byte[16];
        in.get(salt);
        in.getInt();
        int idLength = in.get() & 0xff;
        byte[] asPublic = new byte[idLength];
        in.get(asPublic);
        byte[] ciphertext = new byte[in.remaining()];
        in.get(ciphertext);

        byte[] uaPublic = EcKeys.uncompressed((ECPublicKey) device.getPublic());
        byte[][] keyAndNonce = deriveKeyAndNonce(
                ecdh(device, EcKeys.publicKey(asPublic)), authSecret, uaPublic, asPublic, salt);
        byte[] padded = aesGcm(Cipher.DECRYPT_MODE, keyAndNonce[0], keyAndNonce[1], ciphertext);

        int end = padded.length - 1;
        while (end >= 0 && padded[end] == 0) end--;
        if (end < 0 || padded[end] != 0x02) {
            throw new IllegalStateException("마지막 레코드 표시가 없다");
        }
        return Arrays.copyOf(padded, end);
    }

    private static byte[][] deriveKeyAndNonce(byte[] ecdhSecret, byte[] authSecret,
                                              byte[] uaPublic, byte[] asPublic, byte[] salt) {
        // 기기 인증 비밀과 두 공개키를 섞는다. 중계가 공개키를 바꿔치기하면 여기서 어긋난다.
        byte[] keyInfo = concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), uaPublic, asPublic);
        byte[] ikm = hkdf(authSecret, ecdhSecret, keyInfo, 32);

        byte[] prk = hmac(salt, ikm);
        byte[] cek = expand(prk, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), 16);
        byte[] nonce = expand(prk, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), 12);
        return new byte[][] {cek, nonce};
    }

    private static byte[] ecdh(KeyPair mine, ECPublicKey theirs) {
        try {
            KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
            agreement.init(mine.getPrivate());
            agreement.doPhase(theirs, true);
            return agreement.generateSecret();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("ECDH 실패", e);
        }
    }

    private static byte[] aesGcm(int mode, byte[] key, byte[] nonce, byte[] input) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            return cipher.doFinal(input);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES-GCM 실패", e);
        }
    }

    /** HKDF(RFC 5869) = 추출 한 번 + 확장 */
    private static byte[] hkdf(byte[] salt, byte[] ikm, byte[] info, int length) {
        return expand(hmac(salt, ikm), info, length);
    }

    /** 32바이트 이하만 쓰므로 확장은 한 블록이면 된다 */
    private static byte[] expand(byte[] prk, byte[] info, int length) {
        return Arrays.copyOf(hmac(prk, concat(info, new byte[] {0x01})), length);
    }

    private static byte[] hmac(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC 실패", e);
        }
    }

    private static KeyPair ephemeralKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(EcKeys.params());
            return generator.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("임시키 생성 실패", e);
        }
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) out.writeBytes(part);
        return out.toByteArray();
    }
}
