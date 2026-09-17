package com.nursecollab.domain.notification.push;

import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;

/**
 * P-256 키를 웹 푸시가 주고받는 모양(비압축 65바이트 점, 32바이트 스칼라)과 JDK 키 사이에서 옮긴다.
 *
 * 브라우저는 공개키를 X.509 가 아니라 0x04 로 시작하는 날 점으로 준다. JDK 는 그 모양을 바로 읽지 못한다.
 */
final class EcKeys {

    private static final ECParameterSpec P256 = p256();

    private EcKeys() {}

    static ECPublicKey publicKey(byte[] uncompressed) {
        if (uncompressed.length != 65 || uncompressed[0] != 0x04) {
            throw new IllegalArgumentException("P-256 비압축 공개키가 아니다");
        }
        BigInteger x = new BigInteger(1, Arrays.copyOfRange(uncompressed, 1, 33));
        BigInteger y = new BigInteger(1, Arrays.copyOfRange(uncompressed, 33, 65));
        try {
            return (ECPublicKey) KeyFactory.getInstance("EC")
                    .generatePublic(new ECPublicKeySpec(new ECPoint(x, y), P256));
        } catch (GeneralSecurityException e) {
            // 곡선 위에 없는 점이면 여기로 온다. 브라우저가 보낸 값이 망가졌다는 뜻이다.
            throw new IllegalArgumentException("P-256 공개키로 읽을 수 없다", e);
        }
    }

    static KeyPair keyPair(byte[] uncompressedPublic, byte[] privateScalar) {
        try {
            var priv = KeyFactory.getInstance("EC")
                    .generatePrivate(new ECPrivateKeySpec(new BigInteger(1, privateScalar), P256));
            return new KeyPair(publicKey(uncompressedPublic), priv);
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("P-256 개인키로 읽을 수 없다", e);
        }
    }

    static byte[] uncompressed(ECPublicKey key) {
        byte[] out = new byte[65];
        out[0] = 0x04;
        copyFixed(key.getW().getAffineX(), out, 1);
        copyFixed(key.getW().getAffineY(), out, 33);
        return out;
    }

    static ECParameterSpec params() {
        return P256;
    }

    /** BigInteger 는 앞자리 0 을 버리거나 부호 바이트를 붙인다. 정확히 32바이트로 맞춘다. */
    private static void copyFixed(BigInteger value, byte[] out, int offset) {
        byte[] raw = value.toByteArray();
        int start = Math.max(0, raw.length - 32);
        int len = raw.length - start;
        System.arraycopy(raw, start, out, offset + 32 - len, len);
    }

    private static ECParameterSpec p256() {
        try {
            AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
            params.init(new ECGenParameterSpec("secp256r1"));
            return params.getParameterSpec(ECParameterSpec.class);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("JDK 에 P-256 이 없다", e);
        }
    }
}
